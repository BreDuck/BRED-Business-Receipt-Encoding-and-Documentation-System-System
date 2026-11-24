package com.bred.rms.ui.components;

import com.bred.rms.ui.theme.RmsColors;

import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/** JScrollPane with the application's slim, theme-aware scroll indicators. */
public final class StyledScrollPane extends JScrollPane {
    public StyledScrollPane(Component view) {
        super(view);
        applyStyle();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (getVerticalScrollBar() != null && getHorizontalScrollBar() != null) applyStyle();
    }

    private void applyStyle() {
        styleScrollBar(getVerticalScrollBar());
        styleScrollBar(getHorizontalScrollBar());
    }

    private static void styleScrollBar(JScrollBar scrollBar) {
        scrollBar.setOpaque(false);
        Dimension size = scrollBar.getOrientation() == JScrollBar.VERTICAL
                ? new Dimension(8, 0) : new Dimension(0, 8);
        scrollBar.setPreferredSize(size);
        scrollBar.setMinimumSize(size);
        scrollBar.setUI(new SlimScrollBarUI());
    }

    private static final class SlimScrollBarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createEmptyButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createEmptyButton();
        }

        @Override
        protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(RmsColors.border());
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                int x = bounds.x + bounds.width / 2;
                g.drawLine(x, bounds.y, x, bounds.y + bounds.height);
            } else {
                int y = bounds.y + bounds.height / 2;
                g.drawLine(bounds.x, y, bounds.x + bounds.width, y);
            }
            g.dispose();
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(RmsColors.primary());
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                int width = 3;
                g.fillRoundRect(bounds.x + (bounds.width - width) / 2, bounds.y,
                        width, bounds.height, width, width);
            } else {
                int height = 3;
                g.fillRoundRect(bounds.x, bounds.y + (bounds.height - height) / 2,
                        bounds.width, height, height, height);
            }
            g.dispose();
        }

        private static JButton createEmptyButton() {
            JButton button = new JButton();
            button.setFocusable(false);
            button.setBorder(javax.swing.BorderFactory.createEmptyBorder());
            Dimension zero = new Dimension(0, 0);
            button.setPreferredSize(zero);
            button.setMinimumSize(zero);
            button.setMaximumSize(zero);
            return button;
        }
    }
}
