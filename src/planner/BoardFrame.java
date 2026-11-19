package planner;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Window;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * The single board window. Holds the board, the "desktop mode unavailable" notice line,
 * and the tray icon menu.
 */
public class BoardFrame extends JFrame {

    private static final String TITLE = "Desktop Planner";

    private final DataStore store;
    private final BoardPanel board;
    private final JPanel notice;
    private TrayIcon trayIcon;
    private final JPanel root;
    private boolean embedded;
    /** True while the window background is transparent (desktop mode). */
    private boolean transparent;
    private Timer attachTimer;

    public BoardFrame(DataStore store) {
        super(TITLE);
        this.store = store;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitApp();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                // Desktop mode: when the user goes to another window, drop back to the desktop layer.
                // (Not while one of our own dialogs is open on top of the board.)
                if (embedded && e.getOppositeWindow() == null) {
                    DesktopEmbedder.sendToBack();
                }
            }
        });
        setIconImage(Theme.appIcon(32));
        // On the desktop the board has no title bar. If attaching fails, fallBackToWindow() adds it back.
        setUndecorated(DesktopEmbedder.isSupported());

        Theme.setCellSize(store.getCellW(), store.getCellH()); // saved grid (default 80 x 80)
        Theme.setStyle(store.getStyle());                      // saved widget style
        board = new BoardPanel(store, this);
        notice = createNotice();
        notice.setVisible(false);

        root = new JPanel(new BorderLayout());
        root.add(notice, BorderLayout.NORTH);
        root.add(board, BorderLayout.CENTER);
        setContentPane(root);
        setResizable(false);

        // Desktop mode: only the widgets are drawn; the board background is see-through
        // (and click-through) except in edit mode. Needs per-pixel window translucency.
        transparent = isUndecorated() && GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        if (transparent) {
            setBackground(new Color(0, 0, 0, 0));
            root.setOpaque(false);
            board.setTransparent(true);
        }

        // Esc ends edit mode. It works whenever the window has focus.
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "finishEdit");
        root.getActionMap().put("finishEdit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                board.setEditMode(false);
            }
        });

        pack();
        Point saved = store.getBoardLocation();
        if (saved != null && isOnScreen(new Rectangle(saved, getSize()))) {
            setLocation(saved);
        } else {
            placeTopRight();
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                store.setBoardLocation(getLocation());
            }
        });
        installTray();
    }

    private static boolean isOnScreen(Rectangle r) {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        return screen.contains(r.x, r.y) && screen.intersects(r);
    }

    public BoardPanel getBoard() {
        return board;
    }

    private void placeTopRight() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int x = Math.max(screen.x, screen.x + screen.width - getWidth() - 24);
        int y = screen.y + 24;
        setLocation(x, y);
    }

    /** Tries to attach the board to the desktop. On failure, keeps a normal window and shows a notice. */
    public void startDesktopMode() {
        if (DesktopEmbedder.tryEmbed(this)) {
            embedded = true;
            System.out.println("[DesktopEmbedder] Board attached to the desktop.");
            if (!store.isBoardSizeSaved()) {
                fitBoardToFreeSpace(false); // first run: pick the largest size that fits between the icons
            }
            // First run (no saved position): the board may move to wherever it covers the fewest icons.
            stayOffIcons(store.getBoardLocation() == null);
            // Every 2 seconds: notice an Explorer restart, and step aside if new icons appeared.
            attachTimer = new Timer(2000, e -> {
                if (!DesktopEmbedder.checkAttached()) {
                    System.err.println("[DesktopEmbedder] Lost the desktop window.");
                    fallBackToWindow();
                } else if (!board.isEditMode()) {
                    stayOffIcons(false);
                    keepAtBottom();
                }
            });
            attachTimer.start();
        } else {
            System.err.println("[DesktopEmbedder] " + DesktopEmbedder.getLastError());
            fallBackToWindow();
        }
    }

    /** Shows the board as a normal window with a title bar and the notice line. */
    private void fallBackToWindow() {
        embedded = false;
        if (attachTimer != null) {
            attachTimer.stop();
            attachTimer = null;
        }
        if (isUndecorated()) {
            setShape(null);
            holes = Collections.emptyList();
            board.setHoles(holes);
            dispose(); // the title bar can only be changed while the window is not displayable
            if (transparent) {
                transparent = false; // a window with a title bar must be opaque
                setBackground(Theme.BOARD_BG);
                root.setOpaque(true);
                board.setTransparent(false);
            }
            setUndecorated(false);
        }
        notice.setVisible(true);
        pack();
        placeTopRight();
        setVisible(true);
    }

    public boolean isEmbedded() {
        return embedded;
    }

    // ------------------------------------------------------------------ staying off desktop icons

    /** Free space kept around each icon (pixels). */
    private static final int ICON_MARGIN = 4;
    /** Step used when searching for a new board position (pixels). */
    private static final int SEARCH_STEP = 8;

    private List<Rectangle> holes = Collections.emptyList();

    /**
     * Keeps the user's desktop files visible and clickable:
     *  1. if the board covers icons and there is a free spot, move there (nearest one);
     *  2. otherwise (allowed only on first run) move to where it covers the fewest icons;
     *  3. cut holes in the board where icons are still covered, so they show through.
     */
    public void stayOffIcons(boolean mayMoveToLeastCovered) {
        if (!embedded) {
            return;
        }
        // The board itself may lie over desktop files; only the widgets must not cover them.
        // 1. find the cells that hold an icon, 2. move widgets off those cells (nearest free
        // cells), 3. if a widget has nowhere to go, cut a hole so the icon still shows.
        board.setIconCells(iconCells(DesktopEmbedder.iconBounds()));
        if (!board.isEditMode()) {
            board.moveWidgetsOffIcons();
        }
        applyHoles(currentIcons(), solidParts());
    }

    /**
     * The parts of the window that hide what is behind it, relative to the window.
     * With a transparent background these are only the widgets; otherwise the whole board.
     */
    private List<Rectangle> solidParts() {
        List<Rectangle> parts = new ArrayList<>();
        if (transparent && !board.isEditMode()) {
            for (WidgetPanel w : board.getWidgets()) {
                parts.add(SwingUtilities.convertRectangle(board, w.getBounds(), getRootPane()));
            }
        } else {
            parts.add(new Rectangle(0, 0, getWidth(), getHeight()));
        }
        return parts;
    }

    private static List<Rectangle> wholeWindow(int w, int h) {
        return Collections.singletonList(new Rectangle(0, 0, w, h));
    }

    /** Desktop icon rectangles with a small margin around each. */
    private List<Rectangle> currentIcons() {
        List<Rectangle> icons = new ArrayList<>();
        for (Rectangle r : DesktopEmbedder.iconBounds()) {
            Rectangle grown = new Rectangle(r);
            grown.grow(ICON_MARGIN, ICON_MARGIN);
            icons.add(grown);
        }
        return icons;
    }

    /**
     * Nearest position in the work area for a w x h board with no icons under it
     * (or, if !mustBeFree, with the least icon area under it). Null if there is none.
     */
    private Point findSpot(List<Rectangle> icons, boolean mustBeFree, List<Rectangle> solid, int w, int h) {
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (w > area.width || h > area.height) {
            return null;
        }
        Point now = getLocation();
        Point best = null;
        long bestCover = Long.MAX_VALUE;
        long bestDistance = Long.MAX_VALUE;
        for (int y = area.y; y <= area.y + area.height - h; y = nextStep(y, area.y + area.height - h)) {
            for (int x = area.x; x <= area.x + area.width - w; x = nextStep(x, area.x + area.width - w)) {
                long cover = coveredArea(new Point(x, y), solid, icons);
                if (mustBeFree && cover > 0) {
                    continue;
                }
                long distance = Math.abs((long) x - now.x) + Math.abs((long) y - now.y);
                if (cover < bestCover || (cover == bestCover && distance < bestDistance)) {
                    best = new Point(x, y);
                    bestCover = cover;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Next search coordinate; always also tries the last possible one (flush with the edge). */
    private static int nextStep(int v, int last) {
        return v < last && v + SEARCH_STEP > last ? last : v + SEARCH_STEP;
    }

    /** Icon area hidden by the solid parts if the window were at 'at'. */
    private static long coveredArea(Point at, List<Rectangle> solid, List<Rectangle> icons) {
        long sum = 0;
        for (Rectangle part : solid) {
            Rectangle onScreen = new Rectangle(part);
            onScreen.translate(at.x, at.y);
            for (Rectangle icon : icons) {
                Rectangle r = onScreen.intersection(icon);
                if (!r.isEmpty()) {
                    sum += (long) r.width * r.height;
                }
            }
        }
        return sum;
    }

    /** Makes the board see-through (and click-through) where a solid part still overlaps icons. */
    private void applyHoles(List<Rectangle> icons, List<Rectangle> solid) {
        Point me = getLocation();
        List<Rectangle> newHoles = new ArrayList<>();
        for (Rectangle part : solid) {
            Rectangle onScreen = new Rectangle(part);
            onScreen.translate(me.x, me.y);
            for (Rectangle icon : icons) {
                Rectangle r = onScreen.intersection(icon);
                if (!r.isEmpty()) {
                    r.translate(-me.x, -me.y);
                    newHoles.add(r);
                }
            }
        }
        if (newHoles.equals(holes)) {
            return; // nothing changed, avoid reshaping every tick
        }
        holes = newHoles;
        if (transparent) {
            board.setHoles(holes); // painted as fully transparent pixels
            return;
        }
        if (holes.isEmpty()) {
            setShape(null);
            return;
        }
        Area shape = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
        for (Rectangle hole : holes) {
            shape.subtract(new Area(new RoundRectangle2D.Double(hole.x, hole.y, hole.width, hole.height, 12, 12)));
        }
        setShape(shape);
    }

    // ------------------------------------------------------------------ board size

    // ------------------------------------------------------------------ desktop grid

    public boolean isGridMatched() {
        return store.isGridMatched();
    }

    /**
     * Matches the board to this PC's desktop icon grid: the cell size becomes the icon spacing,
     * the board covers the whole desktop, and the cells line up with the icon slots.
     * Widgets are moved off cells that hold icons where possible.
     * Returns null on success, or a message why it could not be done.
     */
    public String matchDesktopGrid() {
        if (!embedded) {
            return "Desktop mode is not available, so the desktop grid cannot be read.";
        }
        List<Rectangle> icons = DesktopEmbedder.iconBounds();
        if (icons.isEmpty()) {
            return "No desktop icons were found, so the icon grid cannot be measured.\n"
                    + "(Is \"Show desktop icons\" turned on?)";
        }
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle first = icons.get(0);
        int cw = first.width;   // icon spacing = one grid cell
        int ch = first.height;
        // Grid start: where the icon columns/rows begin inside the work area.
        int gx = area.x + Math.floorMod(first.x - area.x, cw);
        int gy = area.y + Math.floorMod(first.y - area.y, ch);
        int cols = (area.x + area.width - gx) / cw;
        int rows = (area.y + area.height - gy) / ch;
        if (cols < 2 || rows < 3) {
            return "The desktop grid is too small for the widgets (" + cols + " x " + rows + " cells).";
        }

        Theme.setCellSize(cw, ch);
        store.setCellSize(cw, ch, true);
        store.setGridOrigin(new Point(gx, gy));
        board.setIconCells(iconCellsAt(icons, gx, gy, cw, ch));
        List<WidgetType> skipped = board.setBoardSize(cols, rows);
        holes = Collections.emptyList();
        board.setHoles(holes);
        pack();
        setLocation(gx, gy);
        stayOffIcons(false);
        store.save();
        if (!skipped.isEmpty()) {
            return "Matched, but there was no room for: " + skipped + ". Their data is kept.";
        }
        return null;
    }

    /** Back to the default square cells (used when a fixed board size is chosen). */
    private void leaveDesktopGrid() {
        if (store.isGridMatched()) {
            Theme.setCellSize(Theme.CELL, Theme.CELL);
            store.setCellSize(Theme.CELL, Theme.CELL, false);
            board.setIconCells(Collections.emptySet());
        }
    }

    /** Cells (of the matched grid, relative to the board) that hold a desktop icon. */
    private Set<Point> iconCells(List<Rectangle> icons) {
        Point origin = board.isShowing() ? board.getLocationOnScreen() : getLocation();
        return iconCellsAt(icons, origin.x, origin.y, Theme.cellW(), Theme.cellH());
    }

    /**
     * Board cells (relative to the board at gx, gy) that an icon lies on. The icon's area is
     * shrunk a little first, so an icon that only touches a neighbouring cell does not block it.
     * With a matched grid every icon lands in exactly one cell.
     */
    private static Set<Point> iconCellsAt(List<Rectangle> icons, int gx, int gy, int cw, int ch) {
        Set<Point> cells = new HashSet<>();
        for (Rectangle r : icons) {
            int x0 = r.x + r.width / 5 - gx;
            int y0 = r.y + r.height / 5 - gy;
            int x1 = r.x + r.width * 4 / 5 - gx;
            int y1 = r.y + r.height * 4 / 5 - gy;
            if (x1 < 0 || y1 < 0) {
                continue;
            }
            for (int cx = Math.max(0, x0) / cw; cx <= x1 / cw; cx++) {
                for (int cy = Math.max(0, y0) / ch; cy <= y1 / ch; cy++) {
                    cells.add(new Point(cx, cy));
                }
            }
        }
        return cells;
    }

    /**
     * Before the board is moved the first time: take over the desktop icon grid (cell size =
     * icon spacing, positions on icon slots), keeping the board about the same size on screen.
     * After that, moving the board snaps to icon slots (see snapToGrid).
     */
    public void adoptIconGrid() {
        if (!embedded || store.isGridMatched()) {
            return;
        }
        List<Rectangle> icons = DesktopEmbedder.iconBounds();
        if (icons.isEmpty()) {
            return;
        }
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Rectangle first = icons.get(0);
        int cw = first.width;
        int ch = first.height;
        int cols = Math.max(2, Math.round(board.getWidth() / (float) cw));
        int rows = Math.max(3, Math.round(board.getHeight() / (float) ch));
        Theme.setCellSize(cw, ch);
        store.setCellSize(cw, ch, true);
        store.setGridOrigin(new Point(area.x + Math.floorMod(first.x - area.x, cw),
                area.y + Math.floorMod(first.y - area.y, ch)));
        board.setBoardSize(cols, rows);
        holes = Collections.emptyList();
        board.setHoles(holes);
        pack();
    }

    /** tellUser = false for automatic sizing: then no message box interrupts the user. */
    private void resizeBoard(int cols, int rows, boolean tellUser) {
        List<WidgetType> skipped = board.setBoardSize(cols, rows);
        if (!transparent) {
            setShape(null); // the old holes belong to the old size
        }
        holes = Collections.emptyList();
        board.setHoles(holes);
        pack();
        stayOffIcons(false);
        store.save();
        if (tellUser && !skipped.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (WidgetType t : skipped) {
                names.append(names.length() > 0 ? ", " : "").append(t.getTitle());
            }
            JOptionPane.showMessageDialog(this, "The board is too small for: " + names + ".\n"
                    + "Their data is kept. Make the board bigger, or add them again in Edit layout.",
                    "Board size", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Chooses the largest preset size that fits somewhere on the desktop without covering
     * any icon, and moves the board there. If even the smallest size does not fit, the board
     * is left as it is (holes keep the icons visible).
     */
    public void fitBoardToFreeSpace(boolean tellUser) {
        if (!embedded) {
            return;
        }
        leaveDesktopGrid();
        List<Rectangle> icons = currentIcons();
        for (int[] s : BoardPanel.SIZE_PRESETS) {
            // Layout for this size is not known yet, so require the whole board area to be free.
            int w = s[0] * Theme.cellW();
            int h = s[1] * Theme.cellH();
            Point spot = findSpot(icons, true, wholeWindow(w, h), w, h);
            if (spot != null) {
                if (s[0] != board.getCols() || s[1] != board.getRows()) {
                    resizeBoard(s[0], s[1], false);
                } else {
                    store.setBoardSize(s[0], s[1]);
                }
                setLocation(spot);
                stayOffIcons(false);
                store.save();
                return;
            }
        }
        if (tellUser) {
            int[] smallest = BoardPanel.SIZE_PRESETS[BoardPanel.SIZE_PRESETS.length - 1];
            JOptionPane.showMessageDialog(this, "There is no free space on the desktop even for the smallest board ("
                    + smallest[0] + " x " + smallest[1] + ").\nIcons under the board stay visible through holes.",
                    "Board size", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Sets the board size in cells after the user dragged its edge (keeps the current cell size). */
    public void applyBoardSize(int cols, int rows) {
        resizeBoard(cols, rows, false);
    }

    /**
     * Matched grid: the nearest position where the board's cells line up with the desktop
     * icon grid (inside the work area). Otherwise the position is returned unchanged.
     */
    public Point snapToGrid(Point p) {
        if (!store.isGridMatched()) {
            return p;
        }
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        Point o = store.getGridOrigin();
        int cw = Theme.cellW();
        int ch = Theme.cellH();
        int x = o.x + Math.round((p.x - o.x) / (float) cw) * cw;
        int y = o.y + Math.round((p.y - o.y) / (float) ch) * ch;
        // Keep it on screen, still on the grid.
        while (x + getWidth() > area.x + area.width && x - cw >= area.x) {
            x -= cw;
        }
        while (x < area.x) {
            x += cw;
        }
        while (y + getHeight() > area.y + area.height && y - ch >= area.y) {
            y -= ch;
        }
        while (y < area.y) {
            y += ch;
        }
        return new Point(x, y);
    }

    /** Called after the user dragged the board somewhere in edit mode. */
    public void boardDropped() {
        stayOffIcons(false);
        store.save();
    }

    private JPanel createNotice() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.BANNER_BG);
        p.setBorder(new MatteBorder(0, 0, 1, 0, Theme.CARD_BORDER));
        JLabel text = Theme.label("Desktop mode is unavailable. Running as a normal window.", Theme.SMALL, Theme.TEXT);
        text.setBorder(new EmptyBorder(5, 10, 5, 10));
        JButton close = Theme.flatButton("x");
        close.setForeground(Theme.TEXT_MUTED);
        close.setToolTipText("Hide this message");
        close.addActionListener(e -> {
            p.setVisible(false);
            pack();
        });
        p.add(text, BorderLayout.CENTER);
        p.add(close, BorderLayout.EAST);
        return p;
    }

    public void onEditModeChanged(boolean editing) {
        setTitle(editing ? TITLE + " - editing layout" : TITLE);
        if (editing) {
            holes = Collections.emptyList(); // the 30% background covers the board while editing
            board.setHoles(holes);
        } else {
            stayOffIcons(false); // widgets may have moved onto icons
        }
    }

    private void showBoard() {
        if (embedded) {
            // Shown above other windows until the mouse leaves the board (see keepAtBottom).
            peeking = true;
            DesktopEmbedder.bringForward();
            return;
        }
        setVisible(true);
        setExtendedState(NORMAL);
        toFront();
    }

    /** True while the board was brought forward from the tray menu. */
    private boolean peeking;

    /**
     * Desktop mode: the board belongs under all other windows. Clicking it does not raise it
     * (WS_EX_NOACTIVATE), and this puts it back if anything else did. Skipped while one of our
     * dialogs or menus is open, and while the board is shown from the tray with the mouse on it.
     */
    private void keepAtBottom() {
        for (Window w : getOwnedWindows()) {
            if (w.isShowing()) {
                return;
            }
        }
        if (peeking) {
            PointerInfo pointer = MouseInfo.getPointerInfo();
            if (pointer != null && getBounds().contains(pointer.getLocation())) {
                return;
            }
            peeking = false;
        }
        DesktopEmbedder.sendToBack();
    }

    /** Tray icon menu: a way to reach "Edit layout" even when the board is hard to click. */
    private void installTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem edit = new MenuItem("Edit layout");
        edit.addActionListener(e -> {
            showBoard();
            board.setEditMode(true);
        });
        MenuItem settings = new MenuItem("Settings...");
        settings.addActionListener(e -> SettingsDialog.show(this, store));
        menu.add(settings);
        MenuItem show = new MenuItem("Show board");
        show.addActionListener(e -> showBoard());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> exitApp());
        menu.add(edit);
        menu.add(show);
        menu.addSeparator();
        menu.add(exit);

        trayIcon = new TrayIcon(Theme.appIcon(32), TITLE, menu);
        trayIcon.setImageAutoSize(true);
        // Focus timer: show "study / break is over" as a notification from the tray icon.
        FocusTimer.notifier = (title, text) -> {
            if (trayIcon != null) {
                trayIcon.displayMessage(title, text, TrayIcon.MessageType.INFO);
            }
        };
        trayIcon.addActionListener(e -> showBoard()); // double-click on the icon
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
            System.err.println("[Tray] Cannot add tray icon: " + e.getMessage());
        }
    }

    /** Saves everything and quits. */
    public void exitApp() {
        store.save();
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        dispose();
        System.exit(0);
    }
}
