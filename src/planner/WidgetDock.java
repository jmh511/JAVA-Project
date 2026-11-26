package planner;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * macOS-Dock-like bar shown at the bottom of the screen while editing the layout.
 *  - Drag a widget icon onto the board to add that widget (it slides into the nearest cells).
 *  - Drag a widget from the board onto the dock to remove it (its data is kept).
 * Each widget can be on the board once; icons of widgets already on the board are faded
 * and have a dot under them, like running apps in the macOS Dock.
 */
public class WidgetDock extends JWindow {

    private static final int ICON = 54;
    private static final int TILE_W = 78;
    private static final int PAD = 14;
    private static final int HEIGHT = ICON + 46;

    private final BoardPanel board;
    private final DockPanel panel = new DockPanel();
    private boolean removeHover;

    // Icon being dragged from the dock.
    private WidgetType dragType;
    private JWindow ghost;
    private Point ghostOffset;

    public WidgetDock(BoardPanel board) {
        super(board.getFrame());
        this.board = board;
        setFocusableWindowState(false); // clicking the dock never takes focus away
        setBackground(new Color(0, 0, 0, 0));
        setContentPane(panel);
        int w = PAD * 2 + TILE_W * WidgetType.values().length;
        setSize(w, HEIGHT);
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setLocation(area.x + (area.width - w) / 2, area.y + area.height - HEIGHT - 10);
    }

    /** True if the screen point is over the dock (used while a widget is dragged to it). */
    public boolean containsScreenPoint(Point screen) {
        return isShowing() && getBounds().contains(screen);
    }

    /** Red "remove" look while a widget is held over the dock. */
    public void setRemoveHover(boolean on) {
        if (on != removeHover) {
            removeHover = on;
            panel.repaint();
        }
    }

    /** Redraw the "already on the board" dots. */
    public void refresh() {
        panel.repaint();
    }

    private Rectangle tileBounds(int index) {
        return new Rectangle(PAD + index * TILE_W, 0, TILE_W, HEIGHT);
    }

    private WidgetType typeAt(Point p) {
        int i = (p.x - PAD) / TILE_W;
        if (p.x < PAD || i < 0 || i >= WidgetType.values().length) {
            return null;
        }
        return WidgetType.values()[i];
    }

    // ------------------------------------------------------------------ drag out of the dock

    private void startDrag(WidgetType type, MouseEvent e) {
        dragType = type;
        int index = type.ordinal();
        Rectangle tile = tileBounds(index);
        Point iconOnScreen = new Point(getX() + tile.x + (TILE_W - ICON) / 2, getY() + 10);
        ghostOffset = new Point(e.getXOnScreen() - iconOnScreen.x, e.getYOnScreen() - iconOnScreen.y);

        ghost = new JWindow(this);
        ghost.setFocusableWindowState(false);
        ghost.setBackground(new Color(0, 0, 0, 0));
        ghost.setContentPane(new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setComposite(AlphaComposite.SrcOver.derive(0.85f));
                paintIcon(g2, type, 0, 0, ICON);
                g2.dispose();
            }
        });
        ghost.setBounds(iconOnScreen.x, iconOnScreen.y, ICON, ICON);
        ghost.setVisible(true);
        panel.repaint();
    }

    private void drag(MouseEvent e) {
        Point s = e.getLocationOnScreen();
        ghost.setLocation(s.x - ghostOffset.x, s.y - ghostOffset.y);
        board.getOverlay().showDropPreview(dragType, toBoard(s));
    }

    private void drop(MouseEvent e) {
        Point s = e.getLocationOnScreen();
        Rectangle cells = board.getOverlay().dropCells(dragType, toBoard(s));
        board.getOverlay().clearDropPreview();
        WidgetType type = dragType;
        dragType = null;
        JWindow g = ghost;
        ghost = null;
        if (cells != null) {
            // Slide from where the icon was dropped into the widget's cells.
            Point gb = new Point(g.getX(), g.getY());
            SwingUtilities.convertPointFromScreen(gb, board);
            g.dispose();
            board.addWidgetAnimated(type, cells, new Rectangle(gb.x, gb.y, ICON, ICON));
        } else {
            // Not on the board, or no room there: the icon slides back into the dock.
            Rectangle tile = tileBounds(type.ordinal());
            Rectangle home = new Rectangle(getX() + tile.x + (TILE_W - ICON) / 2, getY() + 10, ICON, ICON);
            SnapAnimator.animate(g.getBounds(), home, g::setBounds, g::dispose);
        }
        panel.repaint();
    }

    /** Screen point to board coordinates, or null when it is not over the board. */
    private Point toBoard(Point screen) {
        if (!board.isShowing()) {
            return null;
        }
        Point p = new Point(screen);
        SwingUtilities.convertPointFromScreen(p, board);
        return new Rectangle(0, 0, board.getWidth(), board.getHeight()).contains(p) && !containsScreenPoint(screen) ? p : null;
    }

    // ------------------------------------------------------------------ painting

    private final class DockPanel extends JComponent {
        DockPanel() {
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    WidgetType t = typeAt(e.getPoint());
                    if (SwingUtilities.isLeftMouseButton(e) && t != null && board.canAdd(t)) {
                        startDrag(t, e);
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragType != null) {
                        drag(e);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (dragType != null) {
                        drop(e);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    WidgetType t = typeAt(e.getPoint());
                    if (t == null) {
                        setToolTipText(null);
                        setCursor(Cursor.getDefaultCursor());
                    } else if (t.allowsMany()) {
                        setToolTipText("Drag onto the board to add a memo (you can add several)");
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    } else if (board.hasWidget(t)) {
                        setToolTipText(t.getTitle() + " is on the board. Drag it here to remove it.");
                        setCursor(Cursor.getDefaultCursor());
                    } else {
                        setToolTipText("Drag onto the board to add " + t.getTitle());
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // Bar background in the current style.
            Color bg;
            Color edge;
            switch (Theme.style()) {
                case DARK:
                    bg = new Color(30, 30, 33, 225);
                    edge = new Color(255, 255, 255, 30);
                    break;
                case GLASS:
                    bg = new Color(26, 38, 40, 150);
                    edge = new Color(255, 255, 255, 120);
                    break;
                default:
                    bg = new Color(255, 255, 255, 215);
                    edge = new Color(0, 0, 0, 28);
            }
            if (removeHover) {
                bg = new Color(255, 59, 48, 215);
                edge = new Color(255, 255, 255, 120);
            }
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w - 1, h - 1, 28, 28);
            if (Theme.style() == Theme.Style.GLASS && !removeHover) {
                g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 55), 0, h * 0.5f, new Color(255, 255, 255, 0)));
                g2.fillRoundRect(0, 0, w - 1, h - 1, 28, 28);
            }
            g2.setColor(edge);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 28, 28);

            if (removeHover) {
                paintTrash(g2, w / 2 - 14, 16);
                g2.setColor(Color.WHITE);
                g2.setFont(Theme.TITLE);
                String s = "Release to remove";
                g2.drawString(s, (w - g2.getFontMetrics().stringWidth(s)) / 2, h - 22);
                g2.dispose();
                return;
            }

            Color labelColor = Theme.style() == Theme.Style.LIGHT ? Theme.TEXT : Color.WHITE;
            g2.setFont(Theme.SMALL);
            FontMetrics fm = g2.getFontMetrics();
            WidgetType[] types = WidgetType.values();
            for (int i = 0; i < types.length; i++) {
                Rectangle t = tileBounds(i);
                int ix = t.x + (TILE_W - ICON) / 2;
                boolean onBoard = board.hasWidget(types[i]);
                boolean dragging = types[i] == dragType;
                Graphics2D gi = (Graphics2D) g2.create();
                if ((onBoard && !types[i].allowsMany()) || dragging) {
                    gi.setComposite(AlphaComposite.SrcOver.derive(0.4f));
                }
                paintIcon(gi, types[i], ix, 10, ICON);
                gi.dispose();

                String label = types[i].getTitle();
                g2.setColor(labelColor);
                g2.drawString(label, t.x + (TILE_W - fm.stringWidth(label)) / 2, 10 + ICON + 15);
                if (onBoard) {
                    g2.setColor(labelColor);
                    g2.fillOval(t.x + TILE_W / 2 - 2, h - 9, 4, 4); // "running" dot
                }
            }
            g2.dispose();
        }
    }

    private static void paintTrash(Graphics2D g2, int x, int y) {
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x, y + 6, x + 28, y + 6);
        g2.drawLine(x + 10, y + 6, x + 11, y + 1);
        g2.drawLine(x + 11, y + 1, x + 17, y + 1);
        g2.drawLine(x + 17, y + 1, x + 18, y + 6);
        g2.drawRoundRect(x + 4, y + 6, 20, 24, 5, 5);
        g2.drawLine(x + 11, y + 12, x + 11, y + 24);
        g2.drawLine(x + 17, y + 12, x + 17, y + 24);
    }

    // ------------------------------------------------------------------ app-style icons

    /** Draws the icon of a widget type, in the style of a macOS app icon. */
    public static void paintIcon(Graphics2D g, WidgetType type, int x, int y, int s) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.translate(x, y);
        int arc = s * 45 / 100;

        // soft shadow under the icon
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fillRoundRect(1, 2, s - 2, s - 1, arc, arc);

        switch (type) {
            case CALENDAR: {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                LocalDate today = LocalDate.now();
                g2.setColor(new Color(0xFF3B30));
                g2.setFont(new Font(Theme.FAMILY, Font.BOLD, s / 6));
                String mon = today.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).toUpperCase(Locale.ENGLISH);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(mon, (s - fm.stringWidth(mon)) / 2, s * 27 / 100);
                g2.setColor(new Color(0x1C1C1E));
                g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, s * 48 / 100));
                fm = g2.getFontMetrics();
                String day = String.valueOf(today.getDayOfMonth());
                g2.drawString(day, (s - fm.stringWidth(day)) / 2, s * 80 / 100);
                break;
            }
            case TIMETABLE: {
                g2.setPaint(new GradientPaint(0, 0, new Color(0x5AC8FA), 0, s, new Color(0x007AFF)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                int c = s / 2;
                int r = s * 32 / 100;
                g2.setColor(Color.WHITE);
                g2.fillOval(c - r, c - r, 2 * r, 2 * r);
                g2.setColor(new Color(0x007AFF));
                g2.setStroke(new BasicStroke(s / 18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(c, c, c, c - r * 65 / 100);
                g2.drawLine(c, c, c + r * 50 / 100, c + r * 20 / 100);
                g2.fillOval(c - 2, c - 2, 4, 4);
                break;
            }
            case HIGHLIGHTS: {
                g2.setPaint(new GradientPaint(0, 0, new Color(0xFFCC00), 0, s, new Color(0xFF9500)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                g2.setColor(Color.WHITE);
                g2.fill(star(s / 2.0, s / 2.0 + 1, s * 0.34, s * 0.14));
                break;
            }
            case CHECKLIST: {
                g2.setPaint(new GradientPaint(0, 0, new Color(0x4CD964), 0, s, new Color(0x28A745)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(s / 11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{s * 27 / 100, s * 44 / 100, s * 74 / 100},
                        new int[]{s * 52 / 100, s * 68 / 100, s * 34 / 100}, 3);
                break;
            }
            case FOCUS: {
                // red tile, white timer ring three-quarters full, small knob on top
                g2.setPaint(new GradientPaint(0, 0, new Color(0xFF6B5E), 0, s, new Color(0xE0352B)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                int r = s * 30 / 100;
                int c = s / 2 + s / 30;
                g2.setStroke(new BasicStroke(s / 12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 90));
                g2.drawOval(s / 2 - r, c - r, 2 * r, 2 * r);
                g2.setColor(Color.WHITE);
                g2.drawArc(s / 2 - r, c - r, 2 * r, 2 * r, 90, -270);
                g2.fillRoundRect(s / 2 - s / 14, c - r - s / 7, s / 7, s / 11, 3, 3);
                break;
            }
            case SYSTEM: {
                // graphite tile with a green activity line, like a system monitor
                g2.setPaint(new GradientPaint(0, 0, new Color(0x4A4A4F), 0, s, new Color(0x1C1C1E)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                g2.setColor(new Color(255, 255, 255, 40));
                g2.setStroke(new BasicStroke(1f));
                for (int i = 1; i <= 3; i++) {
                    int ly = s * (20 + i * 18) / 100;
                    g2.drawLine(s * 15 / 100, ly, s * 85 / 100, ly);
                }
                g2.setColor(new Color(0x34C759));
                g2.setStroke(new BasicStroke(s / 18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int[] px = {15, 30, 40, 52, 62, 72, 85};
                int[] py = {70, 62, 40, 66, 30, 52, 44};
                for (int i = 0; i < px.length; i++) {
                    px[i] = s * px[i] / 100;
                    py[i] = s * py[i] / 100;
                }
                g2.drawPolyline(px, py, px.length);
                break;
            }
            case MEMO: {
                // yellow sticky note with a few lines of writing
                g2.setPaint(new GradientPaint(0, 0, new Color(0xFFE873), 0, s, new Color(0xFFCC00)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                g2.setColor(new Color(0xFFF6C2));
                int m = s * 20 / 100;
                g2.fillRoundRect(m, m, s - 2 * m, s - 2 * m, s / 10, s / 10);
                g2.setColor(new Color(0xC9A400));
                g2.setStroke(new BasicStroke(Math.max(1.5f, s / 30f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < 3; i++) {
                    int ly = s * (36 + i * 14) / 100;
                    g2.drawLine(s * 30 / 100, ly, s * (i == 2 ? 55 : 70) / 100, ly);
                }
                break;
            }
            default: { // TODO
                g2.setPaint(new GradientPaint(0, 0, new Color(0xBF5AF2), 0, s, new Color(0x8E44AD)));
                g2.fillRoundRect(0, 0, s, s - 1, arc, arc);
                g2.setColor(Color.WHITE);
                for (int i = 0; i < 3; i++) {
                    int ly = s * (30 + i * 20) / 100;
                    g2.fillOval(s * 22 / 100, ly - s / 22, s / 11, s / 11);
                    g2.fillRoundRect(s * 38 / 100, ly - s / 34, s * 40 / 100, s / 17 + 1, 3, 3);
                }
            }
        }
        g2.dispose();
    }

    private static Polygon star(double cx, double cy, double outer, double inner) {
        Polygon p = new Polygon();
        for (int i = 0; i < 10; i++) {
            double r = i % 2 == 0 ? outer : inner;
            double a = Math.PI / 2 + i * Math.PI / 5;
            p.addPoint((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy - r * Math.sin(a)));
        }
        return p;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(PAD * 2 + TILE_W * WidgetType.values().length, HEIGHT);
    }
}
