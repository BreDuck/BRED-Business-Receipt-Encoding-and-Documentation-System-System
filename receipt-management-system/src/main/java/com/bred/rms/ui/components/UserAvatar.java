package com.bred.rms.ui.components;

import com.bred.rms.ui.theme.RmsColors;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;

/** Displays a user's profile image when available, otherwise their initials. */
public final class UserAvatar extends JComponent {
    private static final String DUCK_PROFILE_EMAIL = "clarkhilario.bread@gmail.com";
    private static final long BOB_PERIOD_NANOS = 2_800_000_000L;

    private final int imageSize;
    private final boolean animated;
    private final Timer animation;
    private final long animationStartNanos = System.nanoTime();
    private String displayName = "";
    private BufferedImage image;
    private float hover;
    private float hoverTarget;
    private long previousFrameNanos;

    public UserAvatar(int componentSize, int imageSize, boolean animated) {
        this.imageSize = imageSize;
        this.animated = animated;
        setOpaque(false);
        setPreferredSize(new Dimension(componentSize, componentSize));
        setMaximumSize(new Dimension(componentSize, componentSize));
        if (animated) {
            previousFrameNanos = System.nanoTime();
            animation = new Timer(16, event -> advanceAnimation());
            animation.setCoalesce(true);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent event) { hoverTarget = 1; }
                @Override public void mouseExited(MouseEvent event) { hoverTarget = 0; }
            });
        } else {
            animation = null;
        }
    }

    public void setProfile(String name, String email) {
        displayName = name == null ? "" : name;
        image = isDuckProfile(email) ? DuckImage.IMAGE : null;
        repaint();
    }

    public static boolean isDuckProfile(String email) {
        return email != null && DUCK_PROFILE_EMAIL.equals(email.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (animation != null) {
            previousFrameNanos = System.nanoTime();
            animation.start();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int diameter = imageSize + Math.round(hover * 12);
        int x = Math.round((getWidth() - diameter) / 2f - hover * 6);
        int y = (getHeight() - diameter) / 2;
        if (animated) {
            double phase = (System.nanoTime() - animationStartNanos) * (Math.PI * 2 / BOB_PERIOD_NANOS);
            y -= Math.round((float) Math.sin(phase) * 3);
        }
        Ellipse2D circle = new Ellipse2D.Float(x, y, diameter, diameter);
        g.setColor(RmsColors.primaryLight());
        g.fill(circle);
        if (image != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setClip(circle);
            g.drawImage(image, x, y, diameter, diameter, null);
            g.setClip(null);
        } else {
            g.setColor(RmsColors.primary());
            g.setFont(getFont().deriveFont(Font.BOLD, diameter < 44 ? 12f : 17f));
            String initials = initials(displayName);
            var metrics = g.getFontMetrics();
            g.drawString(initials, x + (diameter - metrics.stringWidth(initials)) / 2,
                    y + (diameter + metrics.getAscent() - metrics.getDescent()) / 2);
        }
        g.setColor(RmsColors.primaryAccent());
        g.draw(circle);
        g.dispose();
    }

    @Override
    public void removeNotify() {
        if (animation != null) animation.stop();
        super.removeNotify();
    }

    private void advanceAnimation() {
        long now = System.nanoTime();
        long elapsed = Math.max(0, now - previousFrameNanos);
        previousFrameNanos = now;
        float progress = (float) (1 - Math.exp(-elapsed / 90_000_000.0));
        hover += (hoverTarget - hover) * progress;
        if (Math.abs(hoverTarget - hover) < 0.001f) hover = hoverTarget;
        repaint();
    }

    private static String initials(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty()) return "?";
        String[] parts = normalized.split("\\s+");
        return parts.length == 1
                ? parts[0].substring(0, 1).toUpperCase(Locale.ROOT)
                : (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1))
                        .toUpperCase(Locale.ROOT);
    }

    private static BufferedImage loadDuckImage() {
        var resource = UserAvatar.class.getResource("/com/bred/rms/ui/logo/breducklogo.png");
        if (resource == null) {
            throw new IllegalStateException("Could not load the Creator profile image.");
        }
        try {
            BufferedImage loaded = ImageIO.read(resource);
            if (loaded == null) throw new IllegalStateException("Creator profile image has an unsupported format.");
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the Creator profile image.", e);
        }
    }

    private static final class DuckImage {
        private static final BufferedImage IMAGE = loadDuckImage();
    }
}
