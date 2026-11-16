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
        } else {
            remove(overlay);
            store.save(); // save the layout when editing ends
        }
        frame.onEditModeChanged(on);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ menus

    private JPopupMenu createBoardMenu() {
        JPopupMenu menu = new JPopupMenu();
        addCommonItems(menu);
        return menu;
    }

    private JPopupMenu createWidgetMenu(WidgetPanel w) {
        JPopupMenu menu = new JPopupMenu();
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
        JMenuItem addEvent = new JMenuItem("Add event...");
        addEvent.addActionListener(e -> EventDialog.showAdd(this, store, LocalDate.now(), Times.nextHalfHour()));
        JMenuItem addTodo = new JMenuItem("Add to-do...");
        addTodo.addActionListener(e -> TodoDialog.showAdd(this, store));
        JMenuItem edit = new JMenuItem("Edit layout");
        edit.addActionListener(e -> setEditMode(true));
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> frame.exitApp());
        menu.add(addEvent);
        menu.add(addTodo);
        menu.addSeparator();
        menu.add(edit);
        JMenuItem settings = new JMenuItem("Settings...");
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

    /** true: text stays fully visible on see-through widgets; false: text fades with them. */
    public void setSolidText(boolean solid) {
        store.setSolidText(solid);
        for (WidgetPanel w : widgets) {
            w.setSolidText(solid);
        }
        repaint();
    }
}
