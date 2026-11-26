package planner;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.MenuItem;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;

/**
 * Pop-out (PIP) window: one widget in a small window that stays above all other windows,
 * so it can be seen while the board on the desktop is covered. It shows a second widget of
 * the same type (same data) and leaves the board unchanged.
 *
 * Dropped near the left or right screen edge it docks there. A docked window slides out of
 * the screen when the mouse leaves it, leaving a thin handle; pointing at the handle slides
 * it back. It does not hide while typing in it, while its menu is open, or when pinned.
 *
 * The window has no owner (the board may be attached to the desktop layer, and an owned
 * window would be kept down with it) and is fully opaque (moving see-through windows
 * quickly can flicker on Windows).
 */
public final class PipWindow extends JFrame {

    // ------------------------------------------------------------------ tuning

    /** Largest widget size in the window, in cells. */
    public static final int MAX_CELLS = 3;
    /** Dropped this close (px) to a screen edge: docks to it. */
    public static final int SNAP_DISTANCE = 20;
    /** Width (px) left on screen when hidden. */
    public static final int HANDLE = 8;
    /** Mouse away this long (ms) before a docked window hides. */
    public static final int HIDE_DELAY_MS = 800;
    /** Mouse position check interval (ms). */
    public static final int POLL_MS = 100;
    /** Slide animation: frame interval and total time (ms). false = jump without animation. */
    public static final boolean ANIMATE = true;
    public static final int FRAME_MS = 15;
    public static final int SLIDE_MS = 200;
    private static final int HEADER_H = 24;

    private enum Edge { LEFT, RIGHT, NONE }

    private enum State { SHOWN, HIDDEN, FLOATING }

    // ------------------------------------------------------------------ the single window

    private static PipWindow current;
    private static MenuItem trayItem;

    /** Opens the given board widget in the pop-out window (replacing the one shown before). */
    public static void popOut(BoardPanel board, WidgetPanel w) {
        Rectangle cells = w.getGridRect();
        Point at = null;
        String edge = "NONE";
        boolean pinned = false;
        if (current != null) { // keep the place of the window that is replaced
            at = current.shownLocation();
            edge = current.edge.name();
            pinned = current.pin.isSelected();
        }
        close();
        open(board, w.getType(), w.getRef(), cells.width, cells.height, at, edge, pinned);
        current.persist();
    }

    /** Opens the window saved last time, if any (at start). */
    public static void restore(BoardPanel board) {
        DataStore.PipState s = board.getStore().getPip();
        if (s == null) {
            return;
        }
        if (s.type == WidgetType.MEMO && board.getStore().getMemo(s.ref) == null) {
            board.getStore().setPip(null); // that memo was deleted
            return;
        }
        WidgetPanel same = null;
        for (WidgetPanel w : board.getWidgets()) {
            if (w.getType() == s.type && w.getRef() == s.ref) {
                same = w;
            }
        }
        int cols = same != null ? same.getCols() : s.type.getDefaultCols();
        int rows = same != null ? same.getRows() : s.type.getDefaultRows();
        open(board, s.type, s.ref, cols, rows, new Point(s.x, s.y), s.edge, s.pinned);
    }

    private static void open(BoardPanel board, WidgetType type, long ref, int cols, int rows,
                             Point at, String edge, boolean pinned) {
        current = new PipWindow(board, type, ref, cols, rows);
        current.pin.setSelected(pinned);
        current.place(at, Edge.valueOf(edge));
        current.setVisible(true);
        current.poll.start();
        updateTrayItem();
    }

    /** Closes the window (the widget on the board stays). */
    public static void close() {
        if (current == null) {
            return;
        }
        PipWindow w = current;
        current = null;
        w.poll.stop();
        w.stopSlide();
        w.widget.dispose(); // stop listening to the store
        w.dispose();
        updateTrayItem();
    }

    /** Called when a memo is deleted: its pop-out window goes too. */
    public static void closeIfShowing(WidgetType type, long ref) {
        if (current != null && current.widget.getType() == type && current.widget.getRef() == ref) {
            DataStore store = current.board.getStore();
            close();
            store.setPip(null);
        }
    }

    /** New widget style: build the window again with the new colors, in the same place. */
    public static void restyle(BoardPanel board) {
        if (current == null) {
            return;
        }
        PipWindow old = current;
        WidgetType type = old.widget.getType();
        long ref = old.widget.getRef();
        int cols = old.widget.getCols();
        int rows = old.widget.getRows();
        Point at = old.shownLocation();
        String edge = old.edge.name();
        boolean pinned = old.pin.isSelected();
        boolean visible = old.isVisible();
        close();
        open(board, type, ref, cols, rows, at, edge, pinned);
        if (!visible) {
            current.setVisible(false);
            current.poll.stop();
            updateTrayItem();
        }
    }

    /** At exit: remember the window (position, edge, pin). */
    public static void saveAndHide() {
        if (current != null) {
            current.persist();
            current.poll.stop();
            current.setVisible(false);
        }
    }

    public static PipWindow getCurrent() {
        return current;
    }

    /** Tray menu item: "Hide PIP" / "Show PIP" (disabled when there is no pop-out window). */
    public static MenuItem trayItem() {
        if (trayItem == null) {
            trayItem = new MenuItem("Show PIP");
            trayItem.addActionListener(e -> SwingUtilities.invokeLater(() -> {
                if (current == null) {
                    return;
                }
                if (current.isVisible()) {
                    current.poll.stop();
                    current.setVisible(false);
                } else {
                    current.setVisible(true);
                    current.poll.start();
                }
                updateTrayItem();
            }));
        }
        updateTrayItem();
        return trayItem;
    }

    private static void updateTrayItem() {
        if (trayItem == null) {
            return;
        }
        trayItem.setEnabled(current != null);
        trayItem.setLabel(current != null && current.isVisible() ? "Hide PIP" : "Show PIP");
    }

    // ------------------------------------------------------------------ one window

    private final BoardPanel board;
    private final WidgetPanel widget;
    private final IconButton pin;
    private final JPanel content;
    private final Handle handle = new Handle();
    private Edge edge = Edge.NONE;
    private State state = State.FLOATING;
    private final Timer poll;
    private long outsideSince = -1;
    private Timer slide;
    /** True while the title row is being dragged. */
    private boolean dragging;
    /**
     * True once the user clicked into a text field or typed in this window, until it loses focus. Focus can also
     * land in a memo by itself (e.g. when another window closes); that alone is not "typing".
     */
    private boolean engaged;
    private final java.awt.event.AWTEventListener inputWatcher = ev -> {
        int id = ev.getID();
        boolean clickInText = id == MouseEvent.MOUSE_PRESSED && ev.getSource() instanceof JTextComponent;
        if ((clickInText || id == java.awt.event.KeyEvent.KEY_PRESSED)
                && ev.getSource() instanceof Component
                && SwingUtilities.getWindowAncestor((Component) ev.getSource()) == this) {
            engaged = true;
        }
    };

    private PipWindow(BoardPanel board, WidgetType type, long ref, int cols, int rows) {
        super("Desktop Planner PIP"); // different title: the desktop code finds the board by its own title
        this.board = board;
        setUndecorated(true);
        setType(Window.Type.UTILITY); // no taskbar button, but it can still get keyboard focus
        setAlwaysOnTop(true);
        // Do not take focus when it appears: a memo would count as "typing" and never slide away.
        // A click on it still gives it focus.
        setAutoRequestFocus(false);
        setIconImage(Theme.appIcon(32));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        int c = Math.min(cols, MAX_CELLS);
        int r = Math.min(rows, MAX_CELLS);
        widget = board.newWidget(type, ref);
        widget.setSolidText(board.getStore().isSolidText());
        JPopupMenu menu = Menus.popup();
        for (JMenuItem item : widget.createMenuItems()) {
            menu.add(item);
        }
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        JMenuItem closeItem = Menus.item("Close PIP");
        closeItem.addActionListener(e -> {
            close();
            board.getStore().setPip(null);
        });
        menu.add(closeItem);
        widget.setComponentPopupMenu(menu);

        JPanel cellsPanel = new JPanel(null);
        cellsPanel.setOpaque(false);
        cellsPanel.setPreferredSize(new Dimension(c * Theme.cellW(), r * Theme.cellH()));
        cellsPanel.add(widget);
        widget.setGrid(0, 0, c, r);

        // Title row: drag area with the widget name, pin and close buttons.
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(3, 10, 0, 6));
        header.setPreferredSize(new Dimension(10, HEADER_H));
        JLabel title = Theme.label(type.getTitle(), Theme.SMALL, Theme.wMuted);
        JPanel tools = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
        tools.setOpaque(false);
        pin = new IconButton(IconButton.Kind.PIN, "Pin: do not slide away", Theme.wMuted, () -> { });
        pin.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                pin.setSelected(!pin.isSelected());
                pin.setToolTipText(pin.isSelected() ? "Pinned: stays out" : "Pin: do not slide away");
                persist();
            }
        });
        IconButton closeButton = new IconButton(IconButton.Kind.CLOSE, "Close PIP", Theme.wMuted, () -> {
            close();
            board.getStore().setPip(null);
        });
        tools.add(pin);
        tools.add(closeButton);
        header.add(title, BorderLayout.CENTER);
        header.add(tools, BorderLayout.EAST);
        MouseAdapter drag = new DragHandler();
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        title.addMouseListener(drag);
        title.addMouseMotionListener(drag);
        header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        JPanel column = new JPanel(new BorderLayout());
        column.setOpaque(false);
        column.add(header, BorderLayout.NORTH);
        column.add(cellsPanel, BorderLayout.CENTER);

        content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.pipBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Theme.style() == Theme.Style.LIGHT ? new Color(0, 0, 0, 40) : new Color(255, 255, 255, 40));
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        };
        content.setOpaque(true);
        content.add(column, BorderLayout.CENTER);
        setContentPane(content);

        poll = new Timer(POLL_MS, e -> checkPointer());
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(inputWatcher,
                java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.KEY_EVENT_MASK);
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                engaged = false;
            }
        });
    }

    @Override
    public void dispose() {
        java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(inputWatcher);
        super.dispose();
    }

    /** The widget shown in this window. */
    public WidgetPanel getWidget() {
        return widget;
    }

    /** Usable screen area (main screen without the taskbar). */
    private static Rectangle workArea() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }

    /** First placement: saved spot (moved onto the screen if needed) or the right edge. */
    private void place(Point at, Edge wanted) {
        setEdge(wanted);
        Rectangle wa = workArea();
        if (at == null) {
            at = new Point(wa.x + wa.width - getWidth(), wa.y + wa.height / 3);
            setEdge(Edge.RIGHT);
        }
        int y = clamp(at.y, wa.y, wa.y + wa.height - getHeight());
        if (edge == Edge.LEFT) {
            setLocation(wa.x, y);
            state = State.SHOWN;
        } else if (edge == Edge.RIGHT) {
            setLocation(wa.x + wa.width - getWidth(), y);
            state = State.SHOWN;
        } else {
            setLocation(clamp(at.x, wa.x, wa.x + wa.width - getWidth()), y);
            state = State.FLOATING;
        }
        outsideSince = System.currentTimeMillis(); // hides after the delay unless the mouse comes
    }

    /** Docked windows get the handle strip on the side that stays on screen. */
    private void setEdge(Edge e) {
        edge = e;
        content.remove(handle);
        if (e == Edge.LEFT) {
            content.add(handle, BorderLayout.EAST);
        } else if (e == Edge.RIGHT) {
            content.add(handle, BorderLayout.WEST);
        }
        pack();
    }

    private int shownX() {
        Rectangle wa = workArea();
        return edge == Edge.LEFT ? wa.x : wa.x + wa.width - getWidth();
    }

    private int hiddenX() {
        Rectangle wa = workArea();
        return edge == Edge.LEFT ? wa.x - getWidth() + HANDLE : wa.x + wa.width - HANDLE;
    }

    /** Position when not slid away (what is saved). */
    private Point shownLocation() {
        return edge == Edge.NONE ? getLocation() : new Point(shownX(), getY());
    }

    private void persist() {
        Point p = shownLocation();
        board.getStore().setPip(new DataStore.PipState(widget.getType(), widget.getRef(), p.x, p.y,
                edge.name(), pin.isSelected()));
    }

    // ------------------------------------------------------------------ sliding

    /** Every POLL_MS: slide out when the mouse is on the handle, hide after it has been away. */
    private void checkPointer() {
        if (!isVisible() || dragging || edge == Edge.NONE) {
            return;
        }
        PointerInfo pi = MouseInfo.getPointerInfo();
        if (pi == null) {
            return;
        }
        Point m = pi.getLocation();
        // Only the part on the screen counts (the hidden part may lie over a second monitor).
        Rectangle visible = getBounds().intersection(workArea());
        boolean inside = visible.contains(m);
        if (state == State.HIDDEN) {
            if (inside) {
                state = State.SHOWN;
                slideTo(shownX());
                outsideSince = -1;
            }
            return;
        }
        if (inside || mustStayOut()) {
            outsideSince = -1;
            return;
        }
        long now = System.currentTimeMillis();
        if (outsideSince < 0) {
            outsideSince = now;
        } else if (now - outsideSince >= HIDE_DELAY_MS) {
            state = State.HIDDEN;
            slideTo(hiddenX());
        }
    }

    /** Not while typing here (focus in a text field after the user clicked or typed here), while a menu or one of our dialogs is open, or when pinned. */
    private boolean mustStayOut() {
        if (pin.isSelected()) {
            return true;
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (engaged && focus instanceof JTextComponent && SwingUtilities.getWindowAncestor(focus) == this) {
            return true;
        }
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return true;
        }
        for (Window w : getOwnedWindows()) {
            if (w.isShowing() && !(w instanceof javax.swing.JWindow)) {
                return true; // a dialog opened from the widget
            }
        }
        return false;
    }

    private void slideTo(int targetX) {
        stopSlide();
        if (!ANIMATE) {
            setLocation(targetX, getY());
            return;
        }
        int startX = getX();
        long start = System.currentTimeMillis();
        slide = new Timer(FRAME_MS, null);
        slide.addActionListener(e -> {
            double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) SLIDE_MS);
            double eased = 1 - Math.pow(1 - t, 3); // ease-out: slower at the end
            setLocation((int) Math.round(startX + (targetX - startX) * eased), getY());
            if (t >= 1.0) {
                stopSlide();
            }
        });
        slide.start();
    }

    private void stopSlide() {
        if (slide != null) {
            slide.stop();
            slide = null;
        }
    }

    // ------------------------------------------------------------------ dragging

    /** Drag by the title row; on release dock to a near screen edge or float. */
    private final class DragHandler extends MouseAdapter {
        private Point offset;

        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            stopSlide();
            dragging = true;
            Point s = e.getLocationOnScreen();
            offset = new Point(s.x - getX(), s.y - getY());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (offset == null) {
                return;
            }
            Point s = e.getLocationOnScreen();
            setLocation(s.x - offset.x, s.y - offset.y);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (offset == null) {
                return;
            }
            offset = null;
            dragging = false;
            Rectangle wa = workArea();
            Edge newEdge;
            if (getX() - wa.x <= SNAP_DISTANCE) {
                newEdge = Edge.LEFT;
            } else if (wa.x + wa.width - (getX() + getWidth()) <= SNAP_DISTANCE) {
                newEdge = Edge.RIGHT;
            } else {
                newEdge = Edge.NONE;
            }
            Point keep = getLocation();
            setEdge(newEdge);
            int y = clamp(keep.y, wa.y, wa.y + wa.height - getHeight());
            if (newEdge == Edge.NONE) {
                state = State.FLOATING;
                setLocation(clamp(keep.x, wa.x, wa.x + wa.width - getWidth()), y);
            } else {
                state = State.SHOWN;
                setLocation(keep.x, y);
                slideTo(shownX()); // settle against the edge
            }
            outsideSince = -1;
            persist();
        }
    }

    /** Strip on the screen side of a docked window: the part that stays visible when hidden. */
    private static final class Handle extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(HANDLE, 10);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.withAlpha(Theme.ACCENT, 200));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            g2.drawLine(cx, cy - 12, cx, cy + 12);
            g2.dispose();
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, Math.max(lo, hi)));
    }
}
