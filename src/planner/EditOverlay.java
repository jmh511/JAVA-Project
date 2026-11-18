package planner;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

/**
 * Transparent layer placed over the board in edit mode. It receives all mouse events:
 *  - board right edge / bottom edge / bottom-right corner: resize the board;
 *  - a widget: move it, or resize it at its bottom-right corner;
 *  - empty space: move the whole board.
 * Dragging follows the mouse pixel by pixel; on release everything slides (SnapAnimator)
 * to the nearest grid cells. A widget that would overlap another one slides back instead.
 */
public class EditOverlay extends JComponent {

    /** Widget resize corner size (pixels). */
    private static final int HANDLE = 16;
    /** Width of the board's own resize edges (pixels). */
    private static final int EDGE = 8;
    private static final int RESIZE_RIGHT = 1;
    private static final int RESIZE_BOTTOM = 2;

    private final BoardPanel board;
    private final JButton doneButton = new JButton("Done");

    /** True while a snap animation runs; new drags wait until it is finished. */
    private boolean animating;

    // Widget being dragged.
    private WidgetPanel target;
    private Rectangle startBounds;
    private Rectangle startCells;
    private Point pressPoint;
    private boolean resizing;
    private Rectangle previewCells;
    private boolean previewValid;

    /** True while a dragged widget is held over the dock (release removes it). */
    private boolean overDock;

    // Whole board window being moved.
    private Point windowPress;
    private Point windowStart;

    // Board being resized: RESIZE_RIGHT and/or RESIZE_BOTTOM, 0 = not resizing.
    private int boardResize;
    private Point resizePress;
    private Dimension resizeStart;
    private Dimension previewBoardCells;

    public EditOverlay(BoardPanel board) {
        this.board = board;
        setLayout(null);
        doneButton.setBackground(Theme.ACCENT);
        doneButton.setForeground(Color.WHITE);
        doneButton.setFont(Theme.TITLE);
        doneButton.setFocusPainted(false);
        // Paint our own flat blue background (the Windows look and feel ignores setBackground).
        doneButton.setContentAreaFilled(false);
        doneButton.setBorderPainted(false);
        doneButton.setOpaque(true);
        doneButton.setBorder(new javax.swing.border.EmptyBorder(5, 16, 5, 16));
        doneButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        doneButton.setToolTipText("Finish editing");
        doneButton.addActionListener(e -> board.setEditMode(false));
        add(doneButton);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e) || animating) {
                    return;
                }
                int edge = boardEdgeAt(e.getPoint());
                if (edge != 0) {
                    boardResize = edge;
                    resizePress = e.getLocationOnScreen();
                    resizeStart = board.getSize();
                    return;
                }
                target = board.widgetAt(e.getPoint());
                if (target == null) {
                    // Empty space: drag the whole board window.
                    windowPress = e.getLocationOnScreen();
                    windowStart = board.getFrame().getLocation();
                    return;
                }
                startBounds = target.getBounds();
                startCells = target.getGridRect();
                pressPoint = e.getPoint();
                resizing = nearCorner(startBounds, e.getPoint());
                board.setComponentZOrder(target, 1); // just under the overlay, above other widgets
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (boardResize != 0) {
                    dragBoardEdge(e.getLocationOnScreen());
                } else if (windowPress != null) {
                    Point now = e.getLocationOnScreen();
                    board.getFrame().setLocation(windowStart.x + now.x - windowPress.x,
                            windowStart.y + now.y - windowPress.y);
                } else if (target != null) {
                    dragWidget(e.getPoint(), e.getLocationOnScreen());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showMenu(e);
                    return;
                }
                if (boardResize != 0) {
                    releaseBoardEdge();
                } else if (windowPress != null) {
                    releaseBoardMove();
                } else if (target != null) {
                    releaseWidget();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int edge = boardEdgeAt(e.getPoint());
                int cursor;
                if (edge == (RESIZE_RIGHT | RESIZE_BOTTOM)) {
                    cursor = Cursor.SE_RESIZE_CURSOR;
                } else if (edge == RESIZE_RIGHT) {
                    cursor = Cursor.E_RESIZE_CURSOR;
                } else if (edge == RESIZE_BOTTOM) {
                    cursor = Cursor.S_RESIZE_CURSOR;
                } else {
                    WidgetPanel w = board.widgetAt(e.getPoint());
                    if (w == null) {
                        cursor = Cursor.HAND_CURSOR; // empty space: drag the whole board
                    } else {
                        cursor = nearCorner(w.getBounds(), e.getPoint()) ? Cursor.SE_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
                    }
                }
                setCursor(Cursor.getPredefinedCursor(cursor));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        Dimension d = doneButton.getPreferredSize();
        doneButton.setBounds(width - d.width - 14, 8, d.width, d.height);
    }

    // ------------------------------------------------------------------ board resize

    /** Which board edge is under the point: RESIZE_RIGHT, RESIZE_BOTTOM, both (corner) or 0. */
    private int boardEdgeAt(Point p) {
        int edge = 0;
        if (p.x >= getWidth() - EDGE) {
            edge |= RESIZE_RIGHT;
        }
        if (p.y >= getHeight() - EDGE) {
            edge |= RESIZE_BOTTOM;
        }
        return edge;
    }

    /** Follows the mouse smoothly; never smaller than the widgets, never past the screen edge. */
    private void dragBoardEdge(Point screen) {
        int cw = Theme.cellW();
        int ch = Theme.cellH();
        Dimension min = board.minimumCells();
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Point origin = board.getLocationOnScreen();
        int maxW = area.x + area.width - origin.x;
        int maxH = area.y + area.height - origin.y;

        int w = resizeStart.width;
        int h = resizeStart.height;
        if ((boardResize & RESIZE_RIGHT) != 0) {
            w = clamp(resizeStart.width + screen.x - resizePress.x, min.width * cw, Math.max(min.width * cw, maxW));
        }
        if ((boardResize & RESIZE_BOTTOM) != 0) {
            h = clamp(resizeStart.height + screen.y - resizePress.y, min.height * ch, Math.max(min.height * ch, maxH));
        }
        board.setLiveSize(w, h);
        previewBoardCells = boardCellsFor(w, h);
        repaint();
    }

    /** Nearest whole number of cells for a pixel size (within the allowed range). */
    private Dimension boardCellsFor(int w, int h) {
        int cw = Theme.cellW();
        int ch = Theme.cellH();
        Dimension min = board.minimumCells();
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Point origin = board.getLocationOnScreen();
        int maxCols = Math.max(min.width, (area.x + area.width - origin.x) / cw);
        int maxRows = Math.max(min.height, (area.y + area.height - origin.y) / ch);
        return new Dimension(clamp(Math.round(w / (float) cw), min.width, maxCols),
                clamp(Math.round(h / (float) ch), min.height, maxRows));
    }

    /** Slides the board edge to the nearest cell line, then stores the new size. */
    private void releaseBoardEdge() {
        Dimension cells = boardCellsFor(board.getWidth(), board.getHeight());
        boardResize = 0;
        previewBoardCells = null;
        animating = true;
        Rectangle from = new Rectangle(0, 0, board.getWidth(), board.getHeight());
        Rectangle to = new Rectangle(0, 0, cells.width * Theme.cellW(), cells.height * Theme.cellH());
        SnapAnimator.animate(from, to, r -> board.setLiveSize(r.width, r.height), () -> {
            board.getFrame().applyBoardSize(cells.width, cells.height);
            animating = false;
            repaint();
        });
    }

    // ------------------------------------------------------------------ board move

    /** Matched grid: slide onto the desktop icon grid. Otherwise the board stays where it was dropped. */
    private void releaseBoardMove() {
        windowPress = null;
        BoardFrame frame = board.getFrame();
        Point snapped = frame.snapToGrid(frame.getLocation());
        animating = true;
        Rectangle from = new Rectangle(frame.getLocation(), new Dimension(0, 0));
        Rectangle to = new Rectangle(snapped, new Dimension(0, 0));
        SnapAnimator.animate(from, to, r -> frame.setLocation(r.x, r.y), () -> {
            frame.boardDropped(); // steps off icons if possible, then saves
            animating = false;
        });
    }

    // ------------------------------------------------------------------ widgets

    private void dragWidget(Point p, Point screen) {
        int dx = p.x - pressPoint.x;
        int dy = p.y - pressPoint.y;
        int boardW = board.getCols() * Theme.cellW();
        int boardH = board.getRows() * Theme.cellH();
        if (resizing) {
            WidgetType t = target.getType();
            int minW = t.getMinCols() * Theme.cellW() - 2 * Theme.GAP;
            int minH = t.getMinRows() * Theme.cellH() - 2 * Theme.GAP;
            int w = clamp(startBounds.width + dx, minW, boardW - startBounds.x);
            int h = clamp(startBounds.height + dy, minH, boardH - startBounds.y);
            target.setSize(w, h);
            target.revalidate();
        } else {
            int x = clamp(startBounds.x + dx, 0, boardW - startBounds.width);
            int y = clamp(startBounds.y + dy, 0, boardH - startBounds.height);
            target.setLocation(x, y);
        }
        // Held over the dock: release will remove the widget.
        WidgetDock dock = board.getDock();
        overDock = !resizing && dock != null && dock.containsScreenPoint(screen);
        if (dock != null) {
            dock.setRemoveHover(overDock);
        }
        // Off the board (or over the dock): a floating copy follows the cursor across the screen.
        Rectangle boardOnScreen = new Rectangle(board.getLocationOnScreen(), board.getSize());
        boolean lifted = !resizing && (overDock || !boardOnScreen.contains(screen));
        if (lifted) {
            showGhost(screen);
            target.setVisible(false);
        } else {
            hideGhost();
            target.setVisible(true);
        }
        previewCells = lifted ? null : snap(target.getBounds());
        previewValid = previewCells != null && board.fits(previewCells, target);
        repaint();
    }

    // ------------------------------------------------------------------ floating copy (ghost)

    /** Floating copy of the dragged widget, shown while it is outside the board. */
    private javax.swing.JWindow ghost;
    private java.awt.image.BufferedImage ghostImage;
    /** Where the cursor holds the copy, relative to the copy's top-left corner. */
    private Point ghostGrip;

    private void showGhost(Point screen) {
        if (ghost == null) {
            Rectangle b = target.getBounds();
            // Smaller than the widget, so it does not cover the dock while being dragged.
            double scale = Math.min(0.75, 240.0 / Math.max(b.width, b.height));
            int gw = (int) Math.round(b.width * scale);
            int gh = (int) Math.round(b.height * scale);
            ghostImage = snapshot(target);
            ghostGrip = new Point((int) Math.round((pressPoint.x - startBounds.x) * scale),
                    (int) Math.round((pressPoint.y - startBounds.y) * scale));
            java.awt.Window owner = board.getDock() != null ? board.getDock() : board.getFrame();
            ghost = new javax.swing.JWindow(owner); // owned by the dock, so it is drawn above it
            ghost.setFocusableWindowState(false);
            ghost.setBackground(new Color(0, 0, 0, 0));
            java.awt.image.BufferedImage img = ghostImage;
            ghost.setContentPane(new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.9f));
                    g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
                    g2.dispose();
                }
            });
            ghost.setSize(gw, gh);
        }
        ghost.setLocation(screen.x - ghostGrip.x, screen.y - ghostGrip.y);
        if (!ghost.isVisible()) {
            ghost.setVisible(true);
        }
    }

    private void hideGhost() {
        if (ghost != null && ghost.isVisible()) {
            ghost.setVisible(false);
        }
    }

    private void disposeGhost() {
        if (ghost != null) {
            ghost.dispose();
            ghost = null;
            ghostImage = null;
        }
    }

    /** Picture of a widget, at the display scale so it stays sharp. */
    private java.awt.image.BufferedImage snapshot(JComponent c) {
        double s = Math.max(1.0, c.getGraphicsConfiguration() == null ? 1.0
                : c.getGraphicsConfiguration().getDefaultTransform().getScaleX());
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                (int) Math.ceil(c.getWidth() * s), (int) Math.ceil(c.getHeight() * s),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(s, s);
        c.paint(g);
        g.dispose();
        return img;
    }

    /** Slides the widget into its snapped cells (or back, if they are taken). */
    private void releaseWidget() {
        WidgetPanel w = target;
        if (ghost != null && ghost.isVisible()) {
            releaseLifted(w);
            return;
        }
        disposeGhost();
        Rectangle snapped = snap(w.getBounds());
        Rectangle cells = board.fits(snapped, w) ? snapped : startCells;
        target = null;
        previewCells = null;
        animating = true;
        SnapAnimator.animate(w.getBounds(), cellsToPixels(cells), r -> {
            w.setBounds(r);
            w.revalidate();
            repaint();
        }, () -> {
            w.setGrid(cells);
            animating = false;
            repaint();
        });
    }

    /**
     * The widget was released while floating outside the board.
     * Over the dock: the copy shrinks into the dock and the widget is removed (its data stays).
     * Anywhere else: the copy flies back to where the widget was, and the widget reappears there.
     */
    private void releaseLifted(WidgetPanel w) {
        javax.swing.JWindow g = ghost;
        ghost = null;
        ghostImage = null;
        target = null;
        previewCells = null;
        animating = true;
        WidgetDock dock = board.getDock();
        if (overDock && dock != null) {
            overDock = false;
            Rectangle into = new Rectangle(dock.getX() + dock.getWidth() / 2 - 6, dock.getY() + 24, 12, 12);
            SnapAnimator.animate(g.getBounds(), into, g::setBounds, () -> {
                g.dispose();
                dock.setRemoveHover(false);
                board.removeWidget(w);
                animating = false;
                repaint();
            });
        } else {
            overDock = false;
            if (dock != null) {
                dock.setRemoveHover(false);
            }
            Point home = startBounds.getLocation();
            SwingUtilities.convertPointToScreen(home, board);
            Rectangle back = new Rectangle(home, startBounds.getSize());
            SnapAnimator.animate(g.getBounds(), back, g::setBounds, () -> {
                g.dispose();
                w.setVisible(true);
                w.setGrid(startCells);
                animating = false;
                repaint();
            });
        }
    }

    // ------------------------------------------------------------------ drop from the dock

    /** Preview where a widget dragged from the dock would go (blue = fits, red = no room). */
    public void showDropPreview(WidgetType type, Point boardPoint) {
        if (boardPoint == null) {
            clearDropPreview();
            return;
        }
        Rectangle cells = dropCells(type, boardPoint);
        previewCells = cells != null ? cells : candidate(type, boardPoint, type.getDefaultCols(), type.getDefaultRows());
        previewValid = cells != null;
        repaint();
    }

    public void clearDropPreview() {
        previewCells = null;
        repaint();
    }

    /**
     * Cells for a widget dropped at a board point (centered under the cursor): default size if it
     * fits there, otherwise the minimum size, otherwise null.
     */
    public Rectangle dropCells(WidgetType type, Point boardPoint) {
        if (boardPoint == null) {
            return null;
        }
        int[][] sizes = {{type.getDefaultCols(), type.getDefaultRows()}, {type.getMinCols(), type.getMinRows()}};
        for (int[] s : sizes) {
            Rectangle r = candidate(type, boardPoint, s[0], s[1]);
            if (r.width <= board.getCols() && r.height <= board.getRows() && board.fits(r, null)) {
                return r;
            }
        }
        return null;
    }

    private Rectangle candidate(WidgetType type, Point p, int w, int h) {
        w = Math.min(w, board.getCols());
        h = Math.min(h, board.getRows());
        int col = clamp(p.x / Theme.cellW() - (w - 1) / 2, 0, board.getCols() - w);
        int row = clamp(p.y / Theme.cellH() - (h - 1) / 2, 0, board.getRows() - h);
        return new Rectangle(col, row, w, h);
    }

    public static Rectangle cellsToPixels(Rectangle cells) {
        return new Rectangle(cells.x * Theme.cellW() + Theme.GAP, cells.y * Theme.cellH() + Theme.GAP,
                cells.width * Theme.cellW() - 2 * Theme.GAP, cells.height * Theme.cellH() - 2 * Theme.GAP);
    }

    private static boolean nearCorner(Rectangle b, Point p) {
        return p.x >= b.x + b.width - HANDLE && p.y >= b.y + b.height - HANDLE;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    /** Converts the widget's current pixel bounds to the nearest cells (kept inside the board). */
    private Rectangle snap(Rectangle px) {
        WidgetType t = target.getType();
        if (resizing) {
            int cols = clamp(Math.round((px.width + 2f * Theme.GAP) / Theme.cellW()), t.getMinCols(), board.getCols() - startCells.x);
            int rows = clamp(Math.round((px.height + 2f * Theme.GAP) / Theme.cellH()), t.getMinRows(), board.getRows() - startCells.y);
            return new Rectangle(startCells.x, startCells.y, cols, rows);
        }
        int col = clamp(Math.round((px.x - Theme.GAP) / (float) Theme.cellW()), 0, board.getCols() - startCells.width);
        int row = clamp(Math.round((px.y - Theme.GAP) / (float) Theme.cellH()), 0, board.getRows() - startCells.height);
        return new Rectangle(col, row, startCells.width, startCells.height);
    }

    // ------------------------------------------------------------------ menu

    private void showMenu(MouseEvent e) {
        Point p = e.getPoint();
        WidgetPanel w = board.widgetAt(p);

        JPopupMenu menu = new JPopupMenu();
        // Widgets are added by dragging them from the dock at the bottom of the screen.
        if (w != null) {
            JMenuItem remove = new JMenuItem("Remove " + w.getType().getTitle() + " widget");
            remove.addActionListener(ev -> {
                board.removeWidget(w);
                repaint();
            });
            menu.add(remove);
        }
        menu.addSeparator();
        JMenuItem settings = new JMenuItem("Settings...");
        settings.addActionListener(ev -> SettingsDialog.show(board.getFrame(), board.getStore()));
        menu.add(settings);
        menu.addSeparator();
        JMenuItem done = new JMenuItem("Done");
        done.addActionListener(ev -> board.setEditMode(false));
        menu.add(done);
        menu.show(this, p.x, p.y);
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // While the board is resized: where its edge will snap to.
        if (previewBoardCells != null) {
            int pw = previewBoardCells.width * Theme.cellW();
            int ph = previewBoardCells.height * Theme.cellH();
            g2.setColor(Theme.withAlpha(Theme.ACCENT, 160));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 5f}, 0f));
            g2.drawRect(1, 1, pw - 3, ph - 3);
        }

        // Snapped target position while dragging a widget (blue = ok, red = overlapping).
        if (previewCells != null) {
            Rectangle r = cellsToPixels(previewCells);
            Color c = previewValid ? Theme.ACCENT : Theme.DANGER;
            g2.setColor(Theme.withAlpha(c, 40));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 16, 16);
            g2.setColor(c);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(r.x, r.y, r.width, r.height, 16, 16);
        }

        // Cells that hold desktop icons (matched grid only): faint outline, so they can be avoided.
        g2.setStroke(new BasicStroke(1f));
        for (Point c : board.getIconCells()) {
            int x = c.x * Theme.cellW() + Theme.GAP;
            int y = c.y * Theme.cellH() + Theme.GAP;
            g2.setColor(Theme.withAlpha(Theme.TEXT_MUTED, 40));
            g2.fillRoundRect(x, y, Theme.cellW() - 2 * Theme.GAP, Theme.cellH() - 2 * Theme.GAP, 10, 10);
            g2.setColor(Theme.withAlpha(Theme.TEXT_MUTED, 120));
            g2.drawRoundRect(x, y, Theme.cellW() - 2 * Theme.GAP, Theme.cellH() - 2 * Theme.GAP, 10, 10);
        }

        // Each widget: light veil, dashed outline, resize handle.
        float[] dash = {6f, 4f};
        for (WidgetPanel w : board.getWidgets()) {
            Rectangle b = w.getBounds();
            g2.setComposite(AlphaComposite.SrcOver.derive(0.35f));
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(b.x, b.y, b.width, b.height, 16, 16);
            g2.setComposite(AlphaComposite.SrcOver);
            g2.setColor(w == target ? Theme.ACCENT : Theme.withAlpha(Theme.ACCENT, 150));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            g2.drawRoundRect(b.x, b.y, b.width - 1, b.height - 1, 16, 16);
            g2.setColor(Theme.ACCENT);
            g2.fillRoundRect(b.x + b.width - 12, b.y + b.height - 12, 9, 9, 3, 3);
        }

        // Accent border around the whole board, and its resize grip in the bottom-right corner.
        g2.setStroke(new BasicStroke(3f));
        g2.setColor(Theme.ACCENT);
        g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
        int gx = getWidth() - 4;
        int gy = getHeight() - 4;
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i <= 3; i++) {
            g2.drawLine(gx - 6 * i, gy, gx, gy - 6 * i);
        }

        // Hint at the bottom.
        String hint = "Add: drag an icon from the dock  /  Remove: drag a widget onto the dock  /  "
                + "Resize: drag a corner or the board edge";
        g2.setFont(Theme.SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(hint) + 20;
        if (tw <= getWidth() - 20) {
            int x = (getWidth() - tw) / 2;
            int y = getHeight() - fm.getHeight() - 16;
            g2.setColor(Theme.withAlpha(Theme.TEXT, 200));
            g2.fillRoundRect(x, y, tw, fm.getHeight() + 8, 14, 14);
            g2.setColor(Color.WHITE);
            g2.drawString(hint, x + 10, y + 4 + fm.getAscent());
        }
        g2.setFont(Theme.TINY);
        g2.dispose();
    }
}
