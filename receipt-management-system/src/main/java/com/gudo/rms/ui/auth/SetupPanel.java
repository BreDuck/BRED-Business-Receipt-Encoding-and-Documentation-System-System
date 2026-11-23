package com.gudo.rms.ui.auth;

import com.gudo.rms.ui.theme.RmsColors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import java.awt.Font;
import java.awt.GridBagLayout;

/** Shown instead of the sign-in screen when the Supabase URL / anon key are missing or invalid. */
public final class SetupPanel extends JPanel {
    public SetupPanel() { this(null); }

    /** @param problem extra detail to show (for example a rejected key), or null */
    public SetupPanel(String problem) {
        super(new GridBagLayout());
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        JLabel title = new JLabel("Connect to Supabase");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(RmsColors.PRIMARY);
        JTextArea text = new JTextArea(
                "This app keeps all receipts in your Supabase project, so it needs two settings:\n\n"
                + "  1. RMS_SUPABASE_URL      (your project URL)\n"
                + "  2. RMS_SUPABASE_ANON_KEY (the public anon / publishable key)\n\n"
                + "Copy application.properties.example to application.properties next to the app,\n"
                + "fill in both values, and start the app again.\n"
                + "(You can also set them as environment variables.)\n\n"
                + "Never use the service-role / secret key here. SETUP.md has the full steps."
                + (problem == null || problem.isBlank() ? "" : "\n\nProblem found: " + problem));
        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(text.getFont().deriveFont(14f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        text.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);
        card.add(javax.swing.Box.createVerticalStrut(14));
        card.add(text);
        add(card);
    }
}
