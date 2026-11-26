package com.bred.rms.updates;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compares semantic versions without relying on lexicographical string ordering. */
public final class VersionComparator {
    private static final Pattern VERSION = Pattern.compile(
            "^[vV]?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    private VersionComparator() { }

    /** Returns a negative value, zero, or a positive value as the first version is older, equal, or newer. */
    public static int compare(String first, String second) {
        Version left = parse(first).orElseThrow(() -> invalid(first));
        Version right = parse(second).orElseThrow(() -> invalid(second));
        return left.compareTo(right);
    }

    /** Returns false for malformed values rather than treating invalid input as an update. */
    public static boolean isNewer(String candidate, String installed) {
        Optional<Version> newer = parse(candidate);
        Optional<Version> older = parse(installed);
        return newer.isPresent() && older.isPresent() && newer.get().compareTo(older.get()) > 0;
    }

    /** Removes an optional v prefix and returns the normalized version, or empty for malformed input. */
    public static Optional<String> normalize(String version) {
        return parse(version).map(Version::normalized);
    }

    private static Optional<Version> parse(String value) {
        if (value == null) return Optional.empty();
        Matcher matcher = VERSION.matcher(value.trim());
        if (!matcher.matches()) return Optional.empty();

        List<String> prerelease = new ArrayList<>();
        if (matcher.group(4) != null) {
            for (String identifier : matcher.group(4).split("\\.")) {
                if (identifier.matches("[0-9]+") && identifier.length() > 1 && identifier.startsWith("0")) {
                    return Optional.empty();
                }
                prerelease.add(identifier);
            }
        }
        List<String> build = matcher.group(5) == null
                ? List.of() : List.of(matcher.group(5).split("\\."));
        return Optional.of(new Version(new BigInteger(matcher.group(1)), new BigInteger(matcher.group(2)),
                new BigInteger(matcher.group(3)), List.copyOf(prerelease), build));
    }

    private static IllegalArgumentException invalid(String version) {
        return new IllegalArgumentException("Invalid semantic version: " + version);
    }

    private record Version(BigInteger major, BigInteger minor, BigInteger patch,
                           List<String> prerelease, List<String> build) implements Comparable<Version> {
        @Override
        public int compareTo(Version other) {
            int result = major.compareTo(other.major);
            if (result == 0) result = minor.compareTo(other.minor);
            if (result == 0) result = patch.compareTo(other.patch);
            if (result != 0) return result;
            if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
                return prerelease.isEmpty() == other.prerelease.isEmpty() ? 0
                        : (prerelease.isEmpty() ? 1 : -1);
            }
            int length = Math.min(prerelease.size(), other.prerelease.size());
            for (int i = 0; i < length; i++) {
                String left = prerelease.get(i);
                String right = other.prerelease.get(i);
                boolean leftNumeric = left.matches("[0-9]+");
                boolean rightNumeric = right.matches("[0-9]+");
                if (leftNumeric && rightNumeric) result = new BigInteger(left).compareTo(new BigInteger(right));
                else if (leftNumeric != rightNumeric) result = leftNumeric ? -1 : 1;
                else result = left.compareTo(right);
                if (result != 0) return result;
            }
            return Integer.compare(prerelease.size(), other.prerelease.size());
        }

        private String normalized() {
            String result = major + "." + minor + "." + patch;
            if (!prerelease.isEmpty()) result += "-" + String.join(".", prerelease);
            if (!build.isEmpty()) result += "+" + String.join(".", build);
            return result;
        }
    }
}
