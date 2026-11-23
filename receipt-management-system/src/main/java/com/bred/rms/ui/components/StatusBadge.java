package com.bred.rms.ui.components;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small colored pill used for role and status labels. */
public final class StatusBadge extends JLabel {
    private final Color fill;

    /** Creates a status badge instance. */
    public StatusBadge(String text, Color fill) {
        super(text, SwingConstants.CENTER);
        this.fill = fill;
        setForeground(Color.WHITE);
        setFont(getFont().deriveFont(Font.BOLD, 11f));
        setOpaque(false);
    }

    /** Creates a badge with the supplied text and fill color. */
    public static StatusBadge of(String text, Color fill) { return new StatusBadge(text, fill); }

    /** Turns a raw value such as "in_progress" into a display label. */
    public static String label(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Returns preferred size. */
    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 18, d.height + 6);
    }

    /** Paints component. */
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
