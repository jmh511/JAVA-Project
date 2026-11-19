package planner;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.RenderingHints;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

/**
 * The grid board. No layout manager: each widget is placed with setBounds from its cells.
 */
public class BoardPanel extends JPanel {

    private final DataStore store;
    private final BoardFrame frame;
    private final List<WidgetPanel> widgets = new ArrayList<>();
    private final EditOverlay overlay;
    private WidgetDock dock;
    private boolean editMode;
    /** Board size in cells. */
    private int cols;
    private int rows;
    /** Desktop mode: background see-through except in edit mode. */
    private boolean transparent;
    private List<Rectangle> holes = new ArrayList<>();

    /** Background opacity while editing in desktop mode: 30% of 255. */
    private static final int EDIT_BACKGROUND_ALPHA = 77;

    /** Board sizes offered in the menu (cols x rows), largest first. */
    public static final int[][] SIZE_PRESETS = {{12, 6}, {10, 5}, {10, 4}, {8, 4}, {6, 3}};

    public BoardPanel(DataStore store, BoardFrame frame) {
        super((LayoutManager) null);
        this.store = store;
        this.frame = frame;
        this.cols = store.getBoardCols();
        this.rows = store.getBoardRows();
        setPreferredSize(new Dimension(cols * Theme.cellW(), rows * Theme.cellH()));
        setBackground(Theme.BOARD_BG);
        overlay = new EditOverlay(this);
        setComponentPopupMenu(createBoardMenu());
        store.setLayoutSupplier(this::currentLayouts);

        // Checks the date (midnight) and moves the timetable's "now" line.
        Timer ticker = new Timer(30_000, e -> {
            for (WidgetPanel w : new ArrayList<>(widgets)) {
                w.minuteTick();
            }
        });
        ticker.start();
        updateEmptyButton(); // shown until widgets are added (e.g. a saved empty board)
    }

    /** Widgets may overlap the edit overlay, so let Swing paint children in the safe way. */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /** Desktop mode: no background (see-through) except a 30% one while editing. */
    public void setTransparent(boolean on) {
        transparent = on;
        setOpaque(!on);
        repaint();
    }

    /** Areas (board coordinates) left fully transparent so desktop icons show through and can be clicked. */
    public void setHoles(List<Rectangle> newHoles) {
        holes = new ArrayList<>(newHoles);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (transparent) {
            if (!editMode) {
                return; // nothing: wallpaper and icons stay visible, clicks go to the desktop
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Theme.withAlpha(Theme.BOARD_BG, EDIT_BACKGROUND_ALPHA));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(Theme.withAlpha(Theme.GRID_LINE_EDIT, 200));
            drawGrid(g2);
            g2.dispose();
            return;
        }
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(editMode ? Theme.GRID_LINE_EDIT : Theme.GRID_LINE);
        drawGrid(g2);
        g2.dispose();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (transparent && !editMode && !holes.isEmpty()) {
            // Clear the pixels over icons: alpha 0 is see-through and click-through on Windows.
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.Clear);
            for (Rectangle h : holes) {
                g2.fillRoundRect(h.x, h.y, h.width, h.height, 12, 12);
            }
            g2.dispose();
        }
    }

    /** Grid lines over the whole current area (it can be between cell sizes while being resized). */
    private void drawGrid(Graphics2D g2) {
        int w = getWidth();
        int h = getHeight();
        for (int x = Theme.cellW(); x < w; x += Theme.cellW()) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = Theme.cellH(); y < h; y += Theme.cellH()) {
            g2.drawLine(0, y, w, y);
        }
    }

    /**
     * Sets the board's pixel size directly while the user drags its edge (smooth, not yet
     * in whole cells). The cell count is updated later with setBoardSize.
     */
    public void setLiveSize(int w, int h) {
        setPreferredSize(new Dimension(w, h));
        frame.pack();
    }

    /** No layout manager: only keep the edit layer as large as the board. */
    @Override
    public void doLayout() {
        if (editMode) {
            overlay.setBounds(0, 0, getWidth(), getHeight());
        }
        placeEmptyButton();
    }

    // ------------------------------------------------------------------ empty board button

    /**
     * When every widget has been removed, the transparent board cannot be clicked anywhere.
     * This small button in its bottom-right corner opens edit mode (and the dock) again.
     */
    private final EmptyBoardButton emptyButton = new EmptyBoardButton();

    private void updateEmptyButton() {
        boolean show = widgets.isEmpty() && !editMode;
        if (show && emptyButton.getParent() == null) {
            add(emptyButton);
            setComponentZOrder(emptyButton, 0);
            placeEmptyButton();
            repaint();
        } else if (!show && emptyButton.getParent() != null) {
            remove(emptyButton);
            repaint();
        }
    }

    /** Where the user dragged it last time, or the bottom-right corner; always kept inside the board. */
    private void placeEmptyButton() {
        Dimension d = emptyButton.getPreferredSize();
        Point saved = store.getAddButtonPos();
        int x = saved != null ? saved.x : getWidth() - d.width - 12;
        int y = saved != null ? saved.y : getHeight() - d.height - 12;
        emptyButton.setBounds(clampX(x, d.width), clampY(y, d.height), d.width, d.height);
    }

    private int clampX(int x, int w) {
        return Math.max(0, Math.min(x, getWidth() - w));
    }

    private int clampY(int y, int h) {
        return Math.max(0, Math.min(y, getHeight() - h));
    }

    private final class EmptyBoardButton extends javax.swing.JComponent {
        private boolean hover;

        EmptyBoardButton() {
            setOpaque(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setToolTipText("Add widgets (the board is empty). Drag to move this button.");
            setInheritsPopupMenu(true); // right-click: the board's menu
            java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter() {
                private Point press;      // screen point where the button was pressed
                private Point start;      // button location at that moment
                private boolean dragged;  // moved far enough to be a drag, not a click

                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                        press = e.getLocationOnScreen();
                        start = getLocation();
                        dragged = false;
                    }
                }

                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (press == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    int dx = now.x - press.x;
                    int dy = now.y - press.y;
                    if (!dragged && Math.abs(dx) + Math.abs(dy) > 4) {
                        dragged = true;
                        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                    }
                    if (dragged) {
                        setLocation(clampX(start.x + dx, getWidth()), clampY(start.y + dy, getHeight()));
                        BoardPanel.this.repaint();
                    }
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    if (press == null) {
                        return;
                    }
                    press = null;
                    setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                    if (dragged) {
                        store.setAddButtonPos(getLocation()); // remembered for next time
                    } else if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                        setEditMode(true); // a click, not a drag
                    }
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(44, 44);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            boolean light = Theme.style() == Theme.Style.LIGHT;
            java.awt.Color bg = hover ? Theme.ACCENT
                    : light ? new java.awt.Color(255, 255, 255, 235) : new java.awt.Color(30, 30, 33, 225);
            // Round button with just a "+" (the tooltip explains it).
            g2.setColor(bg);
            g2.fillOval(0, 0, w, h);
            g2.setColor(light && !hover ? new java.awt.Color(0, 0, 0, 30) : new java.awt.Color(255, 255, 255, 40));
            g2.drawOval(0, 0, w, h);
            g2.setColor(hover ? java.awt.Color.WHITE : Theme.ACCENT);
            g2.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            int c = w / 2;
            int arm = w / 5;
            g2.drawLine(c, c - arm, c, c + arm);
            g2.drawLine(c - arm, c, c + arm, c);
            g2.dispose();
        }
    }

    /** Smallest size (in cells) that still holds every widget where it is. */
    public Dimension minimumCells() {
        int c = 2;
        int r = 3; // the timetable needs at least 3 rows
        for (WidgetPanel w : widgets) {
            Rectangle g = w.getGridRect();
            c = Math.max(c, g.x + g.width);
            r = Math.max(r, g.y + g.height);
        }
        return new Dimension(c, r);
    }

    // ------------------------------------------------------------------ widgets

    public BoardFrame getFrame() {
        return frame;
    }

    public DataStore getStore() {
        return store;
    }

    public List<WidgetPanel> getWidgets() {
        return widgets;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /** Default arrangement of the five widgets for the current board size. */
    public void applyDefaultLayout() {
        clearWidgets();
        if (cols == 12 && rows == 6) {
            addWidget(WidgetType.CALENDAR, 0, 0, 3, 3);
            addWidget(WidgetType.HIGHLIGHTS, 0, 3, 3, 3);
            addWidget(WidgetType.TIMETABLE, 3, 0, 3, 6);
            addWidget(WidgetType.TODO, 6, 0, 3, 3);
            addWidget(WidgetType.CHECKLIST, 6, 3, 6, 3);
            return;
        }
        WidgetType[] order = {WidgetType.CALENDAR, WidgetType.TIMETABLE, WidgetType.TODO,
                WidgetType.CHECKLIST, WidgetType.HIGHLIGHTS};
        for (WidgetType t : order) {
            placeBest(t, 0, 0);
        }
    }

    /**
     * Restores saved positions. Widgets that still fit keep their place; the others are moved
     * (and made smaller if needed). Returns the widgets that did not fit at all.
     */
    public List<WidgetType> applyLayouts(List<WidgetLayout> layouts) {
        clearWidgets();
        List<WidgetLayout> later = new ArrayList<>();
        for (WidgetLayout l : layouts) {
            WidgetType t = l.getType();
            Rectangle r = new Rectangle(l.getCol(), l.getRow(),
                    Math.max(t.getMinCols(), l.getCols()), Math.max(t.getMinRows(), l.getRows()));
            if (fits(r, null) && !coversIcons(r)) {
                addWidget(t, r.x, r.y, r.width, r.height);
            } else {
                later.add(l);
            }
        }
        List<WidgetType> skipped = new ArrayList<>();
        for (WidgetLayout l : later) {
            if (!placeBest(l.getType(), l.getCols(), l.getRows())) {
                skipped.add(l.getType());
            }
        }
        return skipped;
    }

    /**
     * Fits the given widgets onto the (new) board size, trying three arrangements and keeping
     * the first one where every widget fits (or else the one that drops the fewest):
     *   1. keep positions where possible, move the rest;
     *   2. the default order, each widget as large as possible;
     *   3. every widget at its minimum size.
     */
    private List<WidgetType> rearrange(List<WidgetLayout> layouts) {
        List<WidgetType> best = applyLayouts(layouts);
        if (best.isEmpty()) {
            return best;
        }
        List<WidgetLayout> bestLayouts = currentLayouts();

        List<WidgetType> types = new ArrayList<>();
        for (WidgetLayout l : layouts) {
            types.add(l.getType());
        }
        for (int strategy = 2; strategy <= 3; strategy++) {
            clearWidgets();
            List<WidgetType> skipped = new ArrayList<>();
            for (WidgetType t : types) {
                boolean placed;
                if (strategy == 2) {
                    placed = placeBest(t, 0, 0);
                } else {
                    placed = placeBest(t, t.getMinCols(), t.getMinRows());
                }
                if (!placed) {
                    skipped.add(t);
                }
            }
            if (skipped.isEmpty()) {
                return skipped;
            }
            if (skipped.size() < best.size()) {
                best = skipped;
                bestLayouts = currentLayouts();
            }
        }
        applyLayouts(bestLayouts);
        return best;
    }

    /**
     * Puts a widget at the first free spot, trying the wanted (or default) size first and then
     * smaller sizes down to the widget's minimum. Returns false if it does not fit anywhere.
     */
    private boolean placeBest(WidgetType t, int wantCols, int wantRows) {
        int startCols = Math.max(t.getMinCols(), wantCols > 0 ? wantCols : t.getDefaultCols());
        int startRows = Math.max(t.getMinRows(), wantRows > 0 ? wantRows : t.getDefaultRows());
        for (int h = Math.min(startRows, rows); h >= t.getMinRows(); h--) {
            for (int w = Math.min(startCols, cols); w >= t.getMinCols(); w--) {
                Point p = findFreeSpot(w, h);
                if (p != null) {
                    addWidget(t, p.x, p.y, w, h);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Changes the board size. Widgets are kept where they still fit and moved otherwise.
     * Returns the widgets that no longer fit (their data is kept in the DataStore).
     */
    public List<WidgetType> setBoardSize(int newCols, int newRows) {
        List<WidgetLayout> current = currentLayouts();
        cols = newCols;
        rows = newRows;
        store.setBoardSize(newCols, newRows);
        setPreferredSize(new Dimension(cols * Theme.cellW(), rows * Theme.cellH()));
        setSize(getPreferredSize());
        List<WidgetType> skipped = rearrange(current);
        if (editMode) {
            overlay.setBounds(0, 0, cols * Theme.cellW(), rows * Theme.cellH());
        }
        revalidate();
        repaint();
        store.save();
        return skipped;
    }

    public WidgetPanel addWidget(WidgetType type, int col, int row, int cols, int rows) {
        WidgetPanel w = createWidget(type);
        w.setOpacity(store.getWidgetOpacity() / 100f);
        w.setSolidText(store.isSolidText());
        w.setComponentPopupMenu(createWidgetMenu(w));
        add(w);
        widgets.add(w);
        w.setGrid(col, row, cols, rows);
        if (editMode) {
            setComponentZOrder(overlay, 0);
        }
        repaint();
        updateEmptyButton();
        return w;
    }

    /** Adds a widget near the given cell if possible, otherwise at the first free spot. */
    public boolean addWidgetNear(WidgetType type, Point cell) {
        int[][] sizes = {{type.getDefaultCols(), type.getDefaultRows()}, {type.getMinCols(), type.getMinRows()}};
        for (int[] s : sizes) {
            if (cell != null) {
                Rectangle r = new Rectangle(cell.x, cell.y, s[0], s[1]);
                if (fits(r, null)) {
                    addWidget(type, r.x, r.y, r.width, r.height);
                    return true;
                }
            }
        }
        for (int[] s : sizes) {
            Point p = findFreeSpot(s[0], s[1]);
            if (p != null) {
                addWidget(type, p.x, p.y, s[0], s[1]);
                return true;
            }
        }
        return false;
    }

    /** Removes the widget from the board only. Its data stays in the DataStore. */
    public void removeWidget(WidgetPanel w) {
        widgets.remove(w);
        remove(w);
        w.dispose();
        repaint();
        updateEmptyButton();
        if (dock != null) {
            dock.refresh();
        }
    }

    /** True if a widget of this type is on the board (each type can be added once from the dock). */
    public boolean hasWidget(WidgetType type) {
        for (WidgetPanel w : widgets) {
            if (w.getType() == type) {
                return true;
            }
        }
        return false;
    }

    /** Adds a widget dropped from the dock: it grows from the dropped icon into its cells. */
    public void addWidgetAnimated(WidgetType type, Rectangle cells, Rectangle fromPixels) {
        WidgetPanel w = addWidget(type, cells.x, cells.y, cells.width, cells.height);
        Rectangle to = w.getBounds();
        w.setBounds(fromPixels);
        SnapAnimator.animate(fromPixels, to, r -> {
            w.setBounds(r);
            w.revalidate();
            overlay.repaint();
        }, () -> {
            w.setGrid(cells);
            overlay.repaint();
        });
        if (dock != null) {
            dock.refresh();
        }
    }

    public EditOverlay getOverlay() {
        return overlay;
    }

    /** The dock while in edit mode, otherwise null. */
    public WidgetDock getDock() {
        return dock;
    }

    private void clearWidgets() {
        for (WidgetPanel w : new ArrayList<>(widgets)) {
            removeWidget(w);
        }
    }

    private WidgetPanel createWidget(WidgetType type) {
        switch (type) {
            case CALENDAR: return new CalendarWidget(store);
            case TIMETABLE: return new TimetableWidget(store);
            case HIGHLIGHTS: return new HighlightsWidget(store);
            case CHECKLIST: return new ChecklistWidget(store);
            case TODO: return new TodoWidget(store);
            default: throw new IllegalArgumentException("Unknown widget " + type);
        }
    }

    // ------------------------------------------------------------------ desktop icon cells

    /** Cells that hold a desktop icon (only known when the grid is matched to the desktop). */
    private final Set<Point> iconCells = new HashSet<>();

    public void setIconCells(Set<Point> cells) {
        iconCells.clear();
        iconCells.addAll(cells);
        if (editMode) {
            overlay.repaint();
        }
    }

    public Set<Point> getIconCells() {
        return iconCells;
    }

    /** True if the cell rectangle contains a desktop icon. Automatic placement avoids these. */
    public boolean coversIcons(Rectangle cells) {
        for (Point p : iconCells) {
            if (cells.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Widgets must not lie on desktop icons. Each widget that does is moved (sliding) to the
     * nearest free cells without icons, at the same size if possible, otherwise smaller down
     * to its minimum. If there is no room at all it stays (the frame then cuts a hole).
     */
    public void moveWidgetsOffIcons() {
        if (iconCells.isEmpty()) {
            return;
        }
        for (WidgetPanel w : new ArrayList<>(widgets)) {
            Rectangle now = w.getGridRect();
            if (!coversIcons(now)) {
                continue;
            }
            Rectangle best = nearestFreeSpot(w, now);
            if (best == null) {
                continue;
            }
            Rectangle from = w.getBounds();
            Rectangle to = EditOverlay.cellsToPixels(best);
            w.setGrid(best); // reserve the cells right away, so the next widget does not take them
            w.setBounds(from);
            SnapAnimator.animate(from, to, r -> {
                w.setBounds(r);
                w.revalidate();
            }, () -> w.setGrid(best));
        }
    }

    /** Closest cells (to 'now') where the widget fits without covering icons, or null. */
    private Rectangle nearestFreeSpot(WidgetPanel w, Rectangle now) {
        WidgetType t = w.getType();
        for (int h = now.height; h >= t.getMinRows(); h--) {
            for (int wd = now.width; wd >= t.getMinCols(); wd--) {
                Rectangle best = null;
                int bestDistance = Integer.MAX_VALUE;
                for (int r = 0; r + h <= rows; r++) {
                    for (int c = 0; c + wd <= cols; c++) {
                        Rectangle spot = new Rectangle(c, r, wd, h);
                        if (!fits(spot, w) || coversIcons(spot)) {
                            continue;
                        }
                        int d = Math.abs(c - now.x) + Math.abs(r - now.y);
                        if (d < bestDistance) {
                            best = spot;
                            bestDistance = d;
                        }
                    }
                }
                if (best != null) {
                    return best;
                }
            }
        }
        return null;
    }

    /** Re-places the current widgets (keeping positions where possible), e.g. after the grid changed. */
    public List<WidgetType> relayout() {
        List<WidgetType> skipped = rearrange(currentLayouts());
        revalidate();
        repaint();
        store.save();
        return skipped;
    }

    /** True when the cell rectangle is inside the board and does not overlap another widget. */
    public boolean fits(Rectangle cells, WidgetPanel ignore) {
        if (cells.x < 0 || cells.y < 0 || cells.x + cells.width > cols || cells.y + cells.height > rows) {
            return false;
        }
        for (WidgetPanel w : widgets) {
            if (w != ignore && w.getGridRect().intersects(cells)) {
                return false;
            }
        }
        return true;
    }

    public Point findFreeSpot(int w, int h) {
        for (int r = 0; r + h <= rows; r++) {
            for (int c = 0; c + w <= cols; c++) {
                Rectangle spot = new Rectangle(c, r, w, h);
                if (fits(spot, null) && !coversIcons(spot)) {
                    return new Point(c, r);
                }
            }
        }
        return null;
    }

    public WidgetPanel widgetAt(Point p) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            if (widgets.get(i).getBounds().contains(p)) {
                return widgets.get(i);
            }
        }
        return null;
    }

    public List<WidgetLayout> currentLayouts() {
        List<WidgetLayout> list = new ArrayList<>();
        for (WidgetPanel w : widgets) {
            Rectangle r = w.getGridRect();
            list.add(new WidgetLayout(w.getType(), r.x, r.y, r.width, r.height));
        }
        return list;
    }

    // ------------------------------------------------------------------ edit mode

    public boolean isEditMode() {
        return editMode;
    }

    public void setEditMode(boolean on) {
        if (on == editMode) {
            return;
        }
        editMode = on;
        if (on) {
            overlay.setBounds(0, 0, getWidth(), getHeight());
            add(overlay);
            setComponentZOrder(overlay, 0);
            dock = new WidgetDock(this); // add / remove widgets by dragging
            dock.setVisible(true);
        } else {
            remove(overlay);
            if (dock != null) {
                dock.dispose();
                dock = null;
            }
            store.save(); // save the layout when editing ends
        }
        updateEmptyButton();
        frame.onEditModeChanged(on);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ menus

    private JPopupMenu createBoardMenu() {
        JPopupMenu menu = Menus.popup();
        addCommonItems(menu);
        return menu;
    }

    private JPopupMenu createWidgetMenu(WidgetPanel w) {
        JPopupMenu menu = Menus.popup();
        List<JMenuItem> own = w.createMenuItems();
        for (JMenuItem item : own) {
            menu.add(item);
        }
        if (!own.isEmpty()) {
            menu.addSeparator();
        }
        addCommonItems(menu);
        return menu;
    }

    private void addCommonItems(JPopupMenu menu) {
        JMenuItem addEvent = Menus.item("Add event...");
        addEvent.addActionListener(e -> EventDialog.showAdd(this, store, LocalDate.now(), Times.nextHalfHour()));
        JMenuItem addTodo = Menus.item("Add to-do...");
        addTodo.addActionListener(e -> TodoDialog.showAdd(this, store));
        JMenuItem edit = Menus.item("Edit layout");
        edit.addActionListener(e -> setEditMode(true));
        JMenuItem exit = Menus.item("Exit");
        exit.addActionListener(e -> frame.exitApp());
        menu.add(addEvent);
        menu.add(addTodo);
        menu.addSeparator();
        menu.add(edit);
        JMenuItem settings = Menus.item("Settings...");
        settings.addActionListener(e -> SettingsDialog.show(frame, store));
        menu.add(settings);
        menu.add(exit);
    }

    /** Opacity of all widget cards (the board background itself stays transparent). */
    public void setWidgetOpacity(int percent) {
        store.setWidgetOpacity(percent);
        for (WidgetPanel w : widgets) {
            w.setOpacity(store.getWidgetOpacity() / 100f);
        }
        repaint();
    }

    /** Changes the widget style; widgets are rebuilt in the same places so they pick up the new colors. */
    public void setWidgetStyle(Theme.Style s) {
        Theme.setStyle(s);
        store.setStyle(s);
        List<WidgetLayout> layouts = currentLayouts();
        clearWidgets();
        for (WidgetLayout l : layouts) {
            addWidget(l.getType(), l.getCol(), l.getRow(), l.getCols(), l.getRows());
        }
        revalidate();
        repaint();
    }

    /** true: text stays fully visible on see-through widgets; false: text fades with them. */
    public void setSolidText(boolean solid) {
        store.setSolidText(solid);
        for (WidgetPanel w : widgets) {
            w.setSolidText(solid);
        }
        repaint();
    }
}
