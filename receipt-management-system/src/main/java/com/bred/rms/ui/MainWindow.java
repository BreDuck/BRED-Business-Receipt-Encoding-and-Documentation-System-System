package com.bred.rms.ui;

import com.bred.rms.config.AppContext;
import com.bred.rms.services.AuthException;
import com.bred.rms.ui.auth.LoginPanel;
import com.bred.rms.ui.auth.SetupPanel;
import com.bred.rms.ui.UiErrors;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.net.URL;
import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/** The application window: setup screen, sign-in screen, or the signed-in shell. */
public final class MainWindow extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final AppContext ctx;
    private final LoginPanel login;
    private Shell shell;

    /** @param ctx the application, or null when Supabase is not configured
     *  @param setupProblem detail to show on the setup screen when ctx is null */
    public MainWindow(AppContext ctx, String setupProblem) {
        super("BRED - Business Receipt Encoding and Documentation System");
        this.ctx = ctx;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 620));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setContentPane(root);
        URL iconUrl = getClass().getResource("/bred-logo.png");
        if (iconUrl != null) {
            setIconImage(Toolkit.getDefaultToolkit().getImage(iconUrl));
        }

        if (ctx == null) {
            login = null;
            root.add(new SetupPanel(setupProblem), "setup");
            cards.show(root, "setup");
            return;
        }
        login = new LoginPanel(this::attemptLogin, (fullName, email, role, companyId, companyName, password, done) ->
                BackgroundTask.run(() -> {
                    try {
                        ctx.auth.registerAccount(fullName, email, role, companyId, companyName, password);
                    } catch (AuthException e) {
                        throw new RegistrationFailure(e.getMessage());
                    }
                    return Boolean.TRUE;
                }, ignored -> done.accept(null), error -> done.accept(
                        error instanceof RegistrationFailure ? error.getMessage() : UiErrors.friendly(error))),
                ctx.userRepository::availableCompanies);
        login.setHint(ctx.loginHint);
        root.add(login, "login");
        cards.show(root, "login");

        installTray();
    }

    /** Hides to the system tray on close instead of exiting. Falls back to normal exit if no tray exists. */
    private void installTray() {
        if (!SystemTray.isSupported()) return;

        Runnable showWindow = () -> {
            setVisible(true);
            setExtendedState(NORMAL);
            toFront();
        };

        Image icon = getIconImage();
        if (icon == null) icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        TrayIcon tray = new TrayIcon(icon, "BRED");
        tray.setImageAutoSize(true);

        MenuItem open = new MenuItem("Open BRED");
        open.addActionListener(e -> showWindow.run());
        MenuItem quit = new MenuItem("Quit");
        quit.addActionListener(e -> System.exit(0));

        PopupMenu menu = new PopupMenu();
        menu.add(open);
        menu.add(quit);
        tray.setPopupMenu(menu);
        tray.addActionListener(e -> showWindow.run()); // double-click the icon

        try {
            SystemTray.getSystemTray().add(tray);
            setDefaultCloseOperation(HIDE_ON_CLOSE); // only now is it safe to hide
        } catch (AWTException e) {
            // tray unavailable: keep EXIT_ON_CLOSE
        }
    }

    private void attemptLogin(String email, char[] password, java.util.function.Consumer<String> done) {
        BackgroundTask.run(() -> ctx.auth.login(email, password), user -> {
            ctx.auth.onSessionExpired(() -> javax.swing.SwingUtilities.invokeLater(() -> {
                showLogin();
                login.showMessage("Your session expired. Please sign in again.");
            }));
            shell = new Shell(ctx, this::signOut);
            root.add(shell, "shell");
            cards.show(root, "shell");
            done.accept(null);
        }, error -> done.accept(error instanceof AuthException ? error.getMessage()
                : "Something went wrong while signing in. Please try again."));
    }

    private void signOut() {
        BackgroundTask.run(() -> { ctx.auth.logout(); return null; }, ignored -> showLogin(), error -> showLogin());
    }

    private void showLogin() {
        if (shell != null) root.remove(shell);
        shell = null;
        login.reset();
        cards.show(root, "login");
        root.revalidate();
        root.repaint();
    }

    private static final class RegistrationFailure extends RuntimeException {
            private RegistrationFailure(String message) { super(message); }
        }
}
