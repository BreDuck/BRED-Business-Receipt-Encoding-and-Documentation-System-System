package com.bred.rms.ui;

import com.bred.rms.ui.components.UserAvatar;
import com.bred.rms.ui.components.StyledScrollPane;
import com.bred.rms.ui.theme.RmsColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;

/** A timed, non-blocking notification with the Creator profile image. */
final class BroadcastToast extends JWindow {
    private final Timer dismiss;

    BroadcastToast(Window owner, String senderName, String senderEmail, String message,
                   Consumer<BroadcastToast> onDismiss) {
        super(owner);
        setFocusableWindowState(false);

        UserAvatar avatar = new UserAvatar(50, 46, false);
        avatar.setProfile(senderName, senderEmail);
        JPanel content = new JPanel(new BorderLayout(12, 0));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(RmsColors.primaryAccent(), 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 12)));
        content.setBackground(RmsColors.surface());
        content.add(avatar, BorderLayout.WEST);

        JPanel text = new JPanel(new BorderLayout(0, 5));
        text.setOpaque(false);
        JLabel title = new JLabel("Creator broadcast");
        title.setForeground(RmsColors.primary());
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 13f));
        JTextArea body = new JTextArea(message, 4, 30);
        body.setEditable(false);
        body.setFocusable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setForeground(RmsColors.text());
        body.setFont(body.getFont().deriveFont(13f));
        javax.swing.JScrollPane bodyScroll = new StyledScrollPane(body);
        bodyScroll.setPreferredSize(new Dimension(300, 82));
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.setOpaque(false);
        bodyScroll.getViewport().setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(bodyScroll, BorderLayout.CENTER);

        JButton close = new JButton("×");
        close.setToolTipText("Dismiss broadcast");
        close.setFocusable(false);
        close.setBorderPainted(false);
        close.setContentAreaFilled(false);
        close.addActionListener(event -> dismiss());
        JPanel closeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeHolder.setOpaque(false);
        closeHolder.add(close);

        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(closeHolder, BorderLayout.NORTH);
        east.add(text, BorderLayout.CENTER);
        content.add(east, BorderLayout.CENTER);
        setContentPane(content);
        pack();

        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setLocation(bounds.x + bounds.width - getWidth() - 18,
                bounds.y + bounds.height - getHeight() - 18);

        dismiss = new Timer(7000, event -> dismiss());
        dismiss.setRepeats(false);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { onDismiss.accept(BroadcastToast.this); }
        });
    }

    void showToast() {
        setVisible(true);
        dismiss.start();
    }

    private void dismiss() {
        dismiss.stop();
        setVisible(false);
        dispose();
    }
}
