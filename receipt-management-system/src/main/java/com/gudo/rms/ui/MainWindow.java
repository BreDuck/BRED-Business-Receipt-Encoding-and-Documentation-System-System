package com.gudo.rms.ui;

import com.gudo.rms.config.AppContext;
import com.gudo.rms.services.AuthException;
import com.gudo.rms.ui.auth.LoginPanel;
import com.gudo.rms.ui.auth.SetupPanel;
import com.gudo.rms.utils.BackgroundTask;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.Dimension;

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
        super("Receipt Management System");
        this.ctx = ctx;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 620));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setContentPane(root);

        if (ctx == null) {
            login = null;
            root.add(new SetupPanel(setupProblem), "setup");
            cards.show(root, "setup");
            return;
        }
        login = new LoginPanel(this::attemptLogin);
        login.setHint(ctx.loginHint);
        root.add(login, "login");
        cards.show(root, "login");
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
}
