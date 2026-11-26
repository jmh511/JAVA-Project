package planner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BooleanSupplier;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Small round icon button drawn with lines (no images): expand, collapse, close and pin.
 * It can be hidden without changing the layout: when 'shownWhen' is false it is not drawn
 * and ignores clicks, but keeps its place.
 */
public class IconButton extends JComponent {

    public enum Kind { EXPAND, COLLAPSE, CLOSE, PIN }

    private static final int SIZE = 18;

    private Kind kind;
    private final Runnable action;
    private BooleanSupplier shownWhen = () -> true;
    private boolean selected;
    private boolean hover;
    private Color color;

    public IconButton(Kind kind, String tooltip, Color color, Runnable action) {
        this.kind = kind;
        this.action = action;
        this.color = color;
        setOpaque(false);
        setToolTipText(tooltip);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && isShownNow()) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        };
        addMouseListener(mouse);
    }

    /** The expand button of a large copy turns into a collapse button. */
    public void setKindCollapse() {
        kind = Kind.COLLAPSE;
        repaint();
    }

    public void setShownWhen(BooleanSupplier condition) {
        shownWhen = condition;
        repaint();
    }

    public boolean isShownNow() {
        return shownWhen.getAsBoolean();
    }

    public void setSelected(boolean on) {
        selected = on;
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    public void setColor(Color c) {
        color = c;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isShownNow()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (hover || (kind == Kind.PIN && selected)) {
            g2.setColor(Theme.withAlpha(color, kind == Kind.PIN && selected ? 60 : 38));
            g2.fillOval(0, 0, w - 1, h - 1);
        }
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int c = w / 2;
        int a = 4; // half length of the drawing
        switch (kind) {
            case EXPAND: // two arrows pointing out to the corners
                g2.drawLine(c + 1, c - 1, c + a, c - a);
                g2.drawLine(c + a - 3, c - a, c + a, c - a);
                g2.drawLine(c + a, c - a, c + a, c - a + 3);
                g2.drawLine(c - 1, c + 1, c - a, c + a);
                g2.drawLine(c - a, c + a - 3, c - a, c + a);
                g2.drawLine(c - a, c + a, c - a + 3, c + a);
                break;
            case COLLAPSE: // two arrows pointing in to the middle
                g2.drawLine(c + 1, c - 1, c + a, c - a);
                g2.drawLine(c + 1, c - 1, c + 4, c - 1);
                g2.drawLine(c + 1, c - 1, c + 1, c - 4);
                g2.drawLine(c - 1, c + 1, c - a, c + a);
                g2.drawLine(c - 1, c + 1, c - 4, c + 1);
                g2.drawLine(c - 1, c + 1, c - 1, c + 4);
                break;
            case CLOSE:
                g2.drawLine(c - a, c - a, c + a, c + a);
                g2.drawLine(c - a, c + a, c + a, c - a);
                break;
            default: // PIN: a push pin, upright when pinned and tilted when not
                if (!selected) {
                    g2.rotate(Math.toRadians(40), c, c);
                }
                g2.drawLine(c - 3, c - 5, c + 3, c - 5);
                g2.drawLine(c - 2, c - 5, c - 2, c);
                g2.drawLine(c + 2, c - 5, c + 2, c);
                g2.drawLine(c - 4, c, c + 4, c);
                g2.drawLine(c, c, c, c + 6);
        }
        g2.dispose();
    }
}
