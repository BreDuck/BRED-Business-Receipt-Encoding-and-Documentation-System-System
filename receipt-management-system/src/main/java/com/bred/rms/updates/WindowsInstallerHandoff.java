package com.bred.rms.updates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Validates a staged MSI and starts an interactive Windows Installer handoff after BRED exits. */
public final class WindowsInstallerHandoff {
    public static final String REQUIRED_UPGRADE_CODE = "7B6F2C1E-4A8D-4F3B-9C1A-5E2D8F0A1B34";

    private static final Logger LOGGER = Logger.getLogger(WindowsInstallerHandoff.class.getName());
    private final Path resultDirectory;
    private final PackageMetadataReader metadataReader;
    private final ProcessStarter processStarter;
    private final boolean windows;

    public WindowsInstallerHandoff() {
        this(defaultResultDirectory(), WindowsInstallerHandoff::readPackageMetadata,
                WindowsInstallerHandoff::startPowerShell, isWindows());
    }

    WindowsInstallerHandoff(Path resultDirectory, PackageMetadataReader metadataReader,
                            ProcessStarter processStarter, boolean windows) {
        this.resultDirectory = Objects.requireNonNull(resultDirectory, "resultDirectory").toAbsolutePath().normalize();
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadataReader");
        this.processStarter = Objects.requireNonNull(processStarter, "processStarter");
        this.windows = windows;
    }

    /**
     * Starts a helper which waits for the current BRED PID to exit, then runs the normal interactive MSI UI.
     * The caller must only invoke this after explicit user confirmation.
     */
    public Handoff startAfterExit(UpdateDownloadManager.DownloadResult download, long applicationPid) {
        Objects.requireNonNull(download, "download");
        if (!windows) throw new UpdateDownloadException("Windows MSI installation is only available on Windows.");
        if (applicationPid <= 0) throw new UpdateDownloadException("The current BRED process could not be identified.");

        Path installer = validateDownloadedInstaller(download);
        PackageMetadata metadata = metadataReader.read(installer);
        validatePackageMetadata(download, metadata);
        Instant appStart = ProcessHandle.of(applicationPid).flatMap(handle -> handle.info().startInstant())
                .orElseThrow(() -> new UpdateDownloadException("BRED could not determine its process start time."));
        Process helper = null;
        Path readyFile = null;
        Path readyTempFile = null;
        try {
            Files.createDirectories(resultDirectory);
            Path resultFile = resultDirectory.resolve("install-" + download.version() + ".properties");
            readyFile = resultDirectory.resolve("install-" + download.version() + ".ready");
            readyTempFile = readyFile.resolveSibling(readyFile.getFileName() + ".tmp");
            Path logFile = resultDirectory.resolve("install-handoff.log");
            Files.deleteIfExists(resultFile);
            Files.deleteIfExists(readyFile);
            Files.deleteIfExists(readyTempFile);
            helper = processStarter.start(
                    powerShellCommand(download, applicationPid, appStart, resultFile, readyFile), logFile);
            if (helper == null) throw new IOException("Windows handoff process did not start.");
            awaitHelperReady(helper, readyFile);
            Files.deleteIfExists(readyFile);
            return new Handoff(download.version(), resultFile, logFile, helper.pid());
        } catch (InterruptedException error) {
            if (helper != null) helper.destroy();
            if (readyFile != null) {
                try {
                    Files.deleteIfExists(readyFile);
                    if (readyTempFile != null) Files.deleteIfExists(readyTempFile);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            Thread.currentThread().interrupt();
            throw new UpdateDownloadException("BRED's installer handoff was interrupted.", error);
        } catch (IOException error) {
            if (helper != null) helper.destroy();
            if (readyFile != null) {
                try {
                    Files.deleteIfExists(readyFile);
                    if (readyTempFile != null) Files.deleteIfExists(readyTempFile);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            LOGGER.log(Level.WARNING, "Unable to start the BRED MSI handoff process.", error);
            throw new UpdateDownloadException("BRED could not start the Windows installer handoff.", error);
        }
    }

    /** Reads and clears the result for a completed handoff, verifying the installed version on this launch. */
    public Optional<String> readPreviousResult(String currentVersion) {
        try {
            if (!Files.isDirectory(resultDirectory)) return Optional.empty();
            try (var files = Files.list(resultDirectory)) {
                List<Path> results = files.toList();
                for (Path readyFile : results.stream().filter(path -> path.getFileName().toString()
                        .matches("install-[0-9]+(?:\\.[0-9]+)*\\.ready(?:\\.tmp)?")).toList()) {
                    Files.deleteIfExists(readyFile);
                }
                for (Path resultFile : results.stream().filter(path -> path.getFileName().toString()
                        .matches("install-[0-9]+(?:\\.[0-9]+)*\\.properties")).toList()) {
                    Properties result = new Properties();
                    try (var reader = Files.newBufferedReader(resultFile, StandardCharsets.UTF_8)) {
                        result.load(reader);
                    }
                    String status = result.getProperty("status", "");
                    String expectedVersion = result.getProperty("version", "");
                    String message = resultMessage(status, expectedVersion, currentVersion);
                    Files.deleteIfExists(resultFile);
                    return Optional.of(message);
                }
            }
            return Optional.empty();
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.log(Level.WARNING, "Unable to read the previous BRED installer result.", error);
            return Optional.of("BRED could not verify the result of the previous update attempt.");
        }
    }

    private Path validateDownloadedInstaller(UpdateDownloadManager.DownloadResult download) {
        if (!VersionComparator.normalize(download.version()).isPresent()
                || !download.version().matches("[0-9]+(?:\\.[0-9]+){2}")) {
            throw new UpdateDownloadException("The downloaded MSI has an invalid BRED version.");
        }
        String filename = "BRED-Setup-" + download.version() + ".msi";
        Path staging = download.stagingDirectory().toAbsolutePath().normalize();
        Path installer = download.installerPath().toAbsolutePath().normalize();
        if (!filename.equals(installer.getFileName().toString()) || !staging.equals(installer.getParent())
                || Files.isSymbolicLink(staging) || !Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(installer)
                || !Files.isRegularFile(installer, LinkOption.NOFOLLOW_LINKS)) {
            throw new UpdateDownloadException("The verified BRED MSI is missing or is outside its update staging directory.");
        }
        try {
            if (Files.size(installer) != download.sizeBytes() || !UpdateDownloadManager.isMsiCompoundFile(installer)) {
                throw new UpdateDownloadException("The staged MSI changed after download verification.");
            }
            byte[] actualDigest = sha256(installer);
            if (!MessageDigest.isEqual(actualDigest, hex(download.sha256()))) {
                throw new UpdateDownloadException("The staged MSI changed after download verification.");
            }
            if (download.expectedSha256().isPresent()) {
                if (!download.checksumVerified()
                        || !MessageDigest.isEqual(actualDigest, hex(download.expectedSha256().orElseThrow()))) {
                    throw new UpdateDownloadException("The staged MSI did not pass its published SHA-256 check.");
                }
            } else if (download.checksumVerified()) {
                throw new UpdateDownloadException("BRED has inconsistent MSI checksum verification data.");
            }
        } catch (IOException error) {
            throw new UpdateDownloadException("BRED could not verify the staged MSI installer.", error);
        }
        return installer;
    }

    private static void validatePackageMetadata(UpdateDownloadManager.DownloadResult download,
                                               PackageMetadata metadata) {
        String actualVersion = VersionComparator.normalize(metadata.productVersion()).orElse("");
        if (!download.version().equals(actualVersion)) {
            throw new UpdateDownloadException("The MSI product version does not match the available BRED version.");
        }
        if (!metadata.perMachine()) {
            throw new UpdateDownloadException("The MSI is not configured for the existing per-machine BRED installation.");
        }
        boolean compatibleRange = metadata.upgradeRanges().stream()
                .filter(range -> REQUIRED_UPGRADE_CODE.equalsIgnoreCase(stripBraces(range.upgradeCode())))
                .anyMatch(range -> range.includes(download.currentVersion()));
        if (!compatibleRange) {
            throw new UpdateDownloadException("The MSI does not use BRED's required upgrade identity for the installed version.");
        }
    }

    private static List<String> powerShellCommand(UpdateDownloadManager.DownloadResult download, long pid,
                                                  Instant startInstant, Path resultFile, Path readyFile) {
        String installer = psQuote(download.installerPath().toString());
        String result = psQuote(resultFile.toString());
        String ready = psQuote(readyFile.toString());
        String expectedVersion = psQuote(download.version());
        String expectedHash = psQuote(download.sha256());
        String expectedStart = psQuote(startInstant.toString());
        String script = """
                $ErrorActionPreference = 'Stop'
                $installer = '%s'
                $result = '%s'
                $ready = '%s'
                $version = '%s'
                $expectedHash = '%s'
                $pidToWait = %d
                $expectedStart = [DateTimeOffset]::Parse('%s').UtcDateTime
                function Write-Result([string]$status, [int]$exitCode) {
                    $text = "status=$status`r`nversion=$version`r`nexitCode=$exitCode`r`n"
                    [IO.File]::WriteAllText($result, $text, [Text.UTF8Encoding]::new($false))
                }
                function Show-Result([string]$text, [string]$title, [string]$icon) {
                    Add-Type -AssemblyName System.Windows.Forms
                    $iconValue = [Enum]::Parse([System.Windows.Forms.MessageBoxIcon], $icon)
                    [void][System.Windows.Forms.MessageBox]::Show($text, $title,
                        [System.Windows.Forms.MessageBoxButtons]::OK, $iconValue)
                }
                $initial = Get-Process -Id $pidToWait -ErrorAction SilentlyContinue
                if ($null -eq $initial -or
                    [Math]::Abs(($initial.StartTime.ToUniversalTime() - $expectedStart).TotalSeconds) -gt 3) {
                    throw 'The BRED process could not be verified.'
                }
                $readyTemp = $ready + '.tmp'
                [IO.File]::WriteAllText($readyTemp, 'READY', [Text.UTF8Encoding]::new($false))
                [IO.File]::Move($readyTemp, $ready)
                while ($true) {
                    $app = Get-Process -Id $pidToWait -ErrorAction SilentlyContinue
                    if ($null -eq $app) { break }
                    if ([Math]::Abs(($app.StartTime.ToUniversalTime() - $expectedStart).TotalSeconds) -gt 3) { break }
                    Start-Sleep -Milliseconds 300
                }
                if (-not (Test-Path -LiteralPath $installer -PathType Leaf)) {
                    Write-Result 'UNAVAILABLE' 2
                    Show-Result 'The downloaded MSI is no longer available. Open BRED and download the update again.' 'BRED update unavailable' 'Warning'
                    exit 2
                }
                try {
                    $actualHash = (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash
                    if ($actualHash -ne $expectedHash) {
                        Write-Result 'UNVERIFIED' 3
                        Show-Result 'The staged MSI changed after verification. BRED was not updated.' 'BRED update not installed' 'Warning'
                        exit 3
                    }
                } catch {
                    Write-Result 'UNVERIFIED' 3
                    Show-Result 'Windows could not verify the staged MSI. BRED was not updated.' 'BRED update not installed' 'Warning'
                    exit 3
                }
                try {
                    $arguments = '/i "' + $installer + '" /norestart'
                    $msi = Start-Process -FilePath "$env:SystemRoot\\System32\\msiexec.exe" `
                        -ArgumentList $arguments -Verb RunAs -Wait -PassThru
                    if ($msi.ExitCode -eq 0) {
                        Write-Result 'SUCCESS' 0
                        Show-Result 'Windows Installer reported completion. Reopen BRED to verify the installed version.' 'BRED update finished' 'Information'
                    } elseif ($msi.ExitCode -eq 1602) {
                        Write-Result 'CANCELLED' 1602
                        Show-Result 'The BRED update was cancelled. Reopen BRED to continue with the existing installation.' 'BRED update cancelled' 'Information'
                    } else {
                        Write-Result 'FAILED' $msi.ExitCode
                        Show-Result "The BRED MSI installer failed (code $($msi.ExitCode)). The existing installation may still be available." 'BRED update failed' 'Error'
                    }
                } catch {
                    $nativeCode = $_.Exception.NativeErrorCode
                    if ($nativeCode -eq 1223) {
                        Write-Result 'UAC_DENIED' 1223
                        Show-Result 'Administrator permission was not approved. BRED was not updated.' 'BRED update not installed' 'Warning'
                    } else {
                        Write-Result 'FAILED' 1
                        Show-Result 'Windows could not start the BRED MSI installer. Reopen BRED and try again.' 'BRED update failed' 'Error'
                    }
                }
                """.formatted(installer, result, ready, expectedVersion, expectedHash, pid, expectedStart);
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded);
    }

    private static String resultMessage(String status, String expectedVersion, String currentVersion) {
        return switch (status) {
            case "SUCCESS" -> VersionComparator.normalize(currentVersion)
                    .filter(version -> VersionComparator.isNewer(version, expectedVersion)
                            || VersionComparator.compare(version, expectedVersion) == 0)
                    .map(version -> "BRED was updated to version " + version + ".")
                    .orElse("Windows Installer completed, but BRED is still running version "
                            + currentVersion + ". The update may not have completed.");
            case "CANCELLED" -> "The BRED update installation was cancelled.";
            case "UAC_DENIED" -> "Administrator permission was not approved. BRED was not updated.";
            case "UNAVAILABLE" -> "The downloaded MSI was unavailable. Check for updates again.";
            case "UNVERIFIED" -> "The staged MSI changed after verification. BRED was not updated.";
            case "FAILED" -> "The BRED MSI installation failed. Reopen BRED and try again.";
            default -> "BRED could not verify the result of the previous update attempt.";
        };
    }

    private static PackageMetadata readPackageMetadata(Path msi) {
        if (!isWindows()) throw new UpdateDownloadException("MSI metadata inspection is only available on Windows.");
        String path = psQuote(msi.toAbsolutePath().normalize().toString());
        String script = """
                $ErrorActionPreference = 'Stop'
                $installer = New-Object -ComObject WindowsInstaller.Installer
                $database = $installer.OpenDatabase('%s', 0)
                function Get-Property([string]$name) {
                    $view = $database.OpenView("SELECT Value FROM Property WHERE Property='$name'")
                    $view.Execute()
                    $record = $view.Fetch()
                    if ($null -eq $record) { return '' }
                    return $record.StringData(1)
                }
                $productVersion = Get-Property 'ProductVersion'
                $productCode = Get-Property 'ProductCode'
                $allUsers = Get-Property 'ALLUSERS'
                $perUser = Get-Property 'MSIINSTALLPERUSER'
                $view = $database.OpenView('SELECT UpgradeCode, VersionMin, VersionMax, Attributes FROM Upgrade')
                $view.Execute()
                $ranges = @()
                while ($row = $view.Fetch()) {
                    $ranges += (($row.StringData(1), $row.StringData(2), $row.StringData(3),
                        $row.IntegerData(4)) -join '^')
                }
                Write-Output ('BREDMSI|' + $productVersion + '|' + $productCode + '|' + $allUsers + '|' + $perUser + '|' + ($ranges -join ';'))
                """.formatted(path);
        List<String> command = List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                "Bypass", "-Command", script);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroy();
                throw new UpdateDownloadException("BRED timed out while inspecting the MSI upgrade information.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                throw new UpdateDownloadException("BRED could not read the MSI upgrade information.");
            }
            String line = output.lines().filter(value -> value.startsWith("BREDMSI|"))
                    .reduce((first, second) -> second).orElse("");
            String[] fields = line.split("\\|", -1);
            if (fields.length != 6) throw new UpdateDownloadException("The MSI upgrade metadata is incomplete.");
            List<UpgradeRange> ranges = new ArrayList<>();
            if (!fields[5].isBlank()) {
                for (String rawRange : fields[5].split(";")) {
                    String[] range = rawRange.split("\\^", -1);
                    if (range.length != 4) {
                        throw new UpdateDownloadException("The MSI upgrade table contains an invalid version range.");
                    }
                    try {
                        ranges.add(new UpgradeRange(range[0], range[1], range[2], Integer.parseInt(range[3])));
                    } catch (NumberFormatException error) {
                        throw new UpdateDownloadException("The MSI upgrade table contains invalid attributes.", error);
                    }
                }
            }
            boolean perMachine = "1".equals(fields[3]) && !"1".equals(fields[4]);
            return new PackageMetadata(fields[1], fields[2], perMachine, ranges);
        } catch (IOException error) {
            throw new UpdateDownloadException("BRED could not inspect the MSI package.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new UpdateDownloadException("MSI inspection was interrupted.", error);
        }
    }

    private static Process startPowerShell(List<String> command, Path logFile) throws IOException {
        Files.createDirectories(logFile.getParent());
        return new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(logFile.toFile()).start();
    }

    private static void awaitHelperReady(Process helper, Path readyFile) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(readyFile, LinkOption.NOFOLLOW_LINKS)
                    && "READY".equals(Files.readString(readyFile, StandardCharsets.UTF_8))) return;
            if (!helper.isAlive()) throw new IOException("The Windows handoff helper exited before it was ready.");
            Thread.sleep(100);
        }
        helper.destroy();
        throw new IOException("The Windows handoff helper did not become ready.");
    }

    private static String psQuote(String value) {
        return value.replace("'", "''");
    }

    private static String stripBraces(String value) {
        if (value == null) return "";
        return value.trim().replace("{", "").replace("}", "");
    }

    private static byte[] sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private static byte[] hex(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new UpdateDownloadException("BRED's installer verification metadata is invalid.");
        }
        return java.util.HexFormat.of().parseHex(value);
    }

    private static Path defaultResultDirectory() {
        String appData = System.getenv("APPDATA");
        Path root = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Roaming")
                : Path.of(appData);
        return root.resolve("BRED").resolve("updates");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    public record Handoff(String version, Path resultFile, Path logFile, long helperPid) { }

    public record PackageMetadata(String productVersion, String productCode, boolean perMachine,
                                  List<UpgradeRange> upgradeRanges) {
        public PackageMetadata {
            productVersion = Objects.requireNonNull(productVersion, "productVersion");
            productCode = Objects.requireNonNull(productCode, "productCode");
            upgradeRanges = List.copyOf(upgradeRanges);
        }
    }

    public record UpgradeRange(String upgradeCode, String versionMin, String versionMax, int attributes) {
        public UpgradeRange {
            upgradeCode = Objects.requireNonNull(upgradeCode, "upgradeCode");
            versionMin = versionMin == null ? "" : versionMin;
            versionMax = versionMax == null ? "" : versionMax;
        }

        private boolean includes(String version) {
            Optional<String> minimum = parseBound(versionMin);
            Optional<String> maximum = parseBound(versionMax);
            boolean aboveMinimum = minimum.isEmpty()
                    || VersionComparator.compare(version, minimum.orElseThrow()) > 0
                    || (attributes & 0x100) != 0
                    && VersionComparator.compare(version, minimum.orElseThrow()) == 0;
            boolean belowMaximum = maximum.isEmpty()
                    || VersionComparator.compare(version, maximum.orElseThrow()) < 0
                    || (attributes & 0x200) != 0
                    && VersionComparator.compare(version, maximum.orElseThrow()) == 0;
            return aboveMinimum && belowMaximum;
        }

        private static Optional<String> parseBound(String value) {
            if (value.isBlank()) return Optional.empty();
            String normalized = VersionComparator.normalize(value).orElseThrow(
                    () -> new UpdateDownloadException("The MSI upgrade table contains an invalid version bound."));
            return Optional.of(normalized);
        }
    }

    @FunctionalInterface
    interface PackageMetadataReader {
        PackageMetadata read(Path msi);
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command, Path logFile) throws IOException;
    }
}
