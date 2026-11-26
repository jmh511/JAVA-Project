package planner;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Right-click menus drawn in a macOS-like way instead of the default Swing look:
 * rounded panel, no icon column, the hovered item as a rounded blue bar with white text,
 * thin separators, and red text for destructive items. Light menus for the Light style,
 * dark menus for Dark and Liquid Glass.
 */
public final class Menus {

    private static final int ITEM_H = 28;
    private static final int TEXT_PAD = 14;
    private static final int ARC = 14;
    private static final Font FONT = Theme.BODY.deriveFont(13f);

    private Menus() {
    }

    public static JPopupMenu popup() {
        return new MacPopup();
    }

    public static JMenuItem item(String text) {
        return new MacItem(text, false);
    }

    /** Item for something that cannot be undone (shown in red). */
    public static JMenuItem dangerItem(String text) {
        return new MacItem(text, true);
    }

    /** Item with a round color swatch on the right; a ring marks the current choice. */
    public static JMenuItem colorItem(String text, Color swatch, boolean selected) {
        return new MacItem(text, false) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = 14;
                int x = getWidth() - TEXT_PAD - d;
                int y = (getHeight() - d) / 2;
                g2.setColor(swatch);
                g2.fillOval(x, y, d, d);
                g2.setColor(new Color(0, 0, 0, 50));
                g2.drawOval(x, y, d, d);
                if (selected) {
                    g2.setColor(text());
                    g2.setStroke(new java.awt.BasicStroke(1.6f));
                    g2.drawOval(x - 3, y - 3, d + 6, d + 6);
                }
                g2.dispose();
            }
        };
    }

    private static boolean dark() {
        return Theme.style() != Theme.Style.LIGHT;
    }

    private static Color background() {
        return dark() ? new Color(0x2C2C2E) : new Color(0xF9F9FB);
    }

    private static Color edge() {
        return dark() ? new Color(255, 255, 255, 36) : new Color(0, 0, 0, 38);
    }

    private static Color text() {
        return dark() ? new Color(0xF2F2F7) : Theme.TEXT;
    }

    // ------------------------------------------------------------------ the menu panel

    private static final class MacPopup extends JPopupMenu {
        MacPopup() {
            setOpaque(false);
            setBorder(new EmptyBorder(6, 0, 6, 0));
        }

        @Override
        public void addSeparator() {
            add(new Separator());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(edge());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.dispose();
        }

        @Override
        public void paintBorder(Graphics g) {
            // the rounded edge is drawn in paintComponent
        }

        @Override
        public void show(Component invoker, int x, int y) {
            super.show(invoker, x, y);
            makeCornersSeeThrough(invoker);
        }

        /**
         * Swing puts the menu inside its own panel (or its own small window when it does not fit
         * in ours). Those are square and opaque, so make them transparent: then only the rounded
         * shape is visible.
         */
        private void makeCornersSeeThrough(Component invoker) {
            Window own = SwingUtilities.getWindowAncestor(invoker);
            Container c = getParent();
            while (c != null && !(c instanceof JRootPane) && !(c instanceof Window)) {
                if (c instanceof JComponent && !(c instanceof javax.swing.JLayeredPane)) {
                    ((JComponent) c).setOpaque(false);
                }
                c = c.getParent();
            }
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w instanceof JWindow && w != own) {
                try {
                    w.setBackground(new Color(0, 0, 0, 0));
                    ((JWindow) w).getRootPane().setOpaque(false);
                    ((JComponent) ((JWindow) w).getContentPane()).setOpaque(false);
                } catch (RuntimeException e) {
                    // translucent windows not supported: square corners are fine
                }
            }
            repaint();
        }
    }

    // ------------------------------------------------------------------ items

    private static class MacItem extends JMenuItem {
        private final boolean danger;

        MacItem(String text, boolean danger) {
            super(text);
            this.danger = danger;
            setFont(FONT);
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(Math.max(170, fm.stringWidth(getText()) + TEXT_PAD * 2 + 30), ITEM_H);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hot = isEnabled() && (getModel().isArmed() || getModel().isSelected());
            if (hot) {
                g2.setColor(danger ? Theme.IMPORTANT : Theme.ACCENT);
                g2.fillRoundRect(5, 1, getWidth() - 10, getHeight() - 2, 8, 8);
            }
            Color fg;
            if (!isEnabled()) {
                fg = Theme.TEXT_MUTED;
            } else if (hot) {
                fg = Color.WHITE;
            } else {
                fg = danger ? Theme.IMPORTANT : text();
            }
            g2.setColor(fg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), TEXT_PAD, y);
            g2.dispose();
        }
    }

    /** Thin line between groups of items. */
    private static final class Separator extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(10, 9);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 9);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(edge());
            g.fillRect(TEXT_PAD - 4, 4, getWidth() - 2 * (TEXT_PAD - 4), 1);
        }
    }
}
