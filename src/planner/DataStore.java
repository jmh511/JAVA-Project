package planner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Single place that owns all planner data (events, to-dos, checklist items, settings, layout).
 * Widgets register as listeners (observer pattern) and redraw themselves when data changes.
 *
 * File format (data/planner.txt), one item per line, fields separated by '|':
 *   SETTING|timetableStart|0
 *   EVENT|id|title|2026-11-12|09:00|10:30|true
 *   TODO|id|title|2026-11-20|false
 *   CHECK|id|title|2026-11-12|false
 *   LAYOUT|CALENDAR|0|0|3|3
 */
public class DataStore {

    /** Observer interface implemented by every widget. */
    public interface Listener {
        void dataChanged();
    }

    private static final int CHECKLIST_KEEP_DAYS = 14;

    private final Path file;
    private final List<Event> events = new ArrayList<>();
    private final List<Todo> todos = new ArrayList<>();
    private final List<ChecklistItem> checklist = new ArrayList<>();
    private List<WidgetLayout> layouts = new ArrayList<>();
    private int timetableStartHour = 0;
    private int timetableEndHour = 24;
    private long nextId = 1;
    /** Last screen position of the board window, or null when never saved. */
    private java.awt.Point boardLocation;
    /** Board size in cells; boardSizeSaved is false until the size was chosen once. */
    private int boardCols = Theme.COLS;
    private int boardRows = Theme.ROWS;
    private boolean boardSizeSaved;
    /** Opacity of the widget cards in percent (30..100). */
    private int widgetOpacity = 100;
    /** True: text stays 100% visible when widgets are see-through. False: text fades too. */
    private boolean solidText = true;
    /** Position of the "Add widgets" button on an empty board (board coordinates), or null = bottom-right. */
    private java.awt.Point addButtonPos;
    /** Focus timer: study and break length in minutes, and finished study sessions today. */
    private int focusStudyMinutes = 25;
    private int focusBreakMinutes = 5;
    private LocalDate focusDate = LocalDate.now();
    private int focusDoneToday;
    /** Widget style: LIGHT, DARK or GLASS (Theme.Style). */
    private Theme.Style style = Theme.Style.LIGHT;
    /** Cell size in pixels, and whether it (and the board) was matched to the desktop icon grid. */
    private int cellW = Theme.CELL;
    private int cellH = Theme.CELL;
    private boolean gridMatched;
    /** Screen position where the desktop icon grid starts (only meaningful when gridMatched). */
    private java.awt.Point gridOrigin = new java.awt.Point(0, 0);

    private final List<Listener> listeners = new ArrayList<>();
    private Supplier<List<WidgetLayout>> layoutSupplier;

    public DataStore(Path file) {
        this.file = file;
    }

    // ------------------------------------------------------------------ listeners

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** The board gives the current widget positions when the store saves. */
    public void setLayoutSupplier(Supplier<List<WidgetLayout>> supplier) {
        this.layoutSupplier = supplier;
    }

    private void changed() {
        save();
        for (Listener l : new ArrayList<>(listeners)) {
            l.dataChanged();
        }
    }

    // ------------------------------------------------------------------ events

    public Event addEvent(String title, LocalDate date, int startMin, int endMin, boolean important) {
        Event e = new Event(nextId++, title, date, startMin, endMin, important);
        events.add(e);
        changed();
        return e;
    }

    public void updateEvent(Event e, String title, LocalDate date, int startMin, int endMin, boolean important) {
        e.setTitle(title);
        e.setDate(date);
        e.setStartMin(startMin);
        e.setEndMin(endMin);
        e.setImportant(important);
        changed();
    }

    public void removeEvent(Event e) {
        if (events.remove(e)) {
            changed();
        }
    }

    public List<Event> eventsOn(LocalDate date) {
        return eventsBetween(date, date);
    }

    /** Events from 'from' to 'to' (both inclusive), sorted by date and start time. */
    public List<Event> eventsBetween(LocalDate from, LocalDate to) {
        List<Event> list = new ArrayList<>();
        for (Event e : events) {
            if (!e.getDate().isBefore(from) && !e.getDate().isAfter(to)) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparing(Event::getDate).thenComparingInt(Event::getStartMin));
        return list;
    }

    public List<Event> importantBetween(LocalDate from, LocalDate to) {
        List<Event> list = eventsBetween(from, to);
        list.removeIf(e -> !e.isImportant());
        return list;
    }

    // ------------------------------------------------------------------ to-dos

    public Todo addTodo(String title, LocalDate due) {
        Todo t = new Todo(nextId++, title, due, false);
        todos.add(t);
        changed();
        return t;
    }

    public void updateTodo(Todo t, String title, LocalDate due) {
        t.setTitle(title);
        t.setDue(due);
        changed();
    }

    public void setTodoDone(Todo t, boolean done) {
        t.setDone(done);
        changed();
    }

    public void removeTodo(Todo t) {
        if (todos.remove(t)) {
            changed();
        }
    }

    /** Unfinished to-dos first (by due date), finished ones at the bottom. */
    public List<Todo> getTodos() {
        List<Todo> list = new ArrayList<>(todos);
        list.sort(Comparator.comparing(Todo::isDone).thenComparing(Todo::getDue).thenComparingLong(Todo::getId));
        return list;
    }

    // ------------------------------------------------------------------ checklist

    public ChecklistItem addChecklistItem(String title, LocalDate date) {
        ChecklistItem item = new ChecklistItem(nextId++, title, date, false);
        checklist.add(item);
        changed();
        return item;
    }

    public void setChecklistDone(ChecklistItem item, boolean done) {
        item.setDone(done);
        changed();
    }

    public void removeChecklistItem(ChecklistItem item) {
        if (checklist.remove(item)) {
            changed();
        }
    }

    public List<ChecklistItem> checklistOn(LocalDate date) {
        List<ChecklistItem> list = new ArrayList<>();
        for (ChecklistItem item : checklist) {
            if (item.getDate().equals(date)) {
                list.add(item);
            }
        }
        list.sort(Comparator.comparingLong(ChecklistItem::getId));
        return list;
    }

    // ------------------------------------------------------------------ settings / layout

    public int getTimetableStartHour() {
        return timetableStartHour;
    }

    public int getTimetableEndHour() {
        return timetableEndHour;
    }

    public java.awt.Point getBoardLocation() {
        return boardLocation == null ? null : new java.awt.Point(boardLocation);
    }

    public int getBoardCols() {
        return boardCols;
    }

    public int getBoardRows() {
        return boardRows;
    }

    public int getWidgetOpacity() {
        return widgetOpacity;
    }

    /** Percent, limited to 30..100 so widgets never become invisible. Saved immediately. */
    public void setWidgetOpacity(int percent) {
        widgetOpacity = Math.max(30, Math.min(100, percent));
        save();
    }

    public int getCellW() {
        return cellW;
    }

    public int getCellH() {
        return cellH;
    }

    public boolean isGridMatched() {
        return gridMatched;
    }

    public java.awt.Point getGridOrigin() {
        return new java.awt.Point(gridOrigin);
    }

    public void setGridOrigin(java.awt.Point p) {
        gridOrigin = new java.awt.Point(p);
    }

    /** Cell size, and whether it came from the desktop icon grid. Written on the next save. */
    public void setCellSize(int w, int h, boolean matched) {
        cellW = w;
        cellH = h;
        gridMatched = matched;
    }

    public int getFocusStudyMinutes() {
        return focusStudyMinutes;
    }

    public int getFocusBreakMinutes() {
        return focusBreakMinutes;
    }

    public void setFocusMinutes(int study, int brk) {
        focusStudyMinutes = Math.max(1, Math.min(180, study));
        focusBreakMinutes = Math.max(1, Math.min(60, brk));
        save();
    }

    /** Finished study sessions today (starts again from 0 on a new day). */
    public int getFocusDoneToday() {
        return focusDate.equals(LocalDate.now()) ? focusDoneToday : 0;
    }

    public void addFocusSession() {
        if (!focusDate.equals(LocalDate.now())) {
            focusDate = LocalDate.now();
            focusDoneToday = 0;
        }
        focusDoneToday++;
        save();
    }

    public java.awt.Point getAddButtonPos() {
        return addButtonPos == null ? null : new java.awt.Point(addButtonPos);
    }

    public void setAddButtonPos(java.awt.Point p) {
        addButtonPos = p == null ? null : new java.awt.Point(p);
        save();
    }

    public Theme.Style getStyle() {
        return style;
    }

    public void setStyle(Theme.Style s) {
        style = s;
        save();
    }

    public boolean isSolidText() {
        return solidText;
    }

    public void setSolidText(boolean solid) {
        solidText = solid;
        save();
    }

    public boolean isBoardSizeSaved() {
        return boardSizeSaved;
    }

    /** Written on the next save; no listeners are notified. */
    public void setBoardSize(int cols, int rows) {
        boardCols = cols;
        boardRows = rows;
        boardSizeSaved = true;
    }

    /** Remembered for the next start. Written on the next save; no listeners are notified. */
    public void setBoardLocation(java.awt.Point p) {
        boardLocation = p == null ? null : new java.awt.Point(p);
    }

    public void setTimetableRange(int startHour, int endHour) {
        if (startHour < 0 || endHour > 24 || endHour <= startHour) {
            throw new IllegalArgumentException("Invalid timetable range");
        }
        timetableStartHour = startHour;
        timetableEndHour = endHour;
        changed();
    }

    public List<WidgetLayout> getLayouts() {
        return Collections.unmodifiableList(layouts);
    }

    // ------------------------------------------------------------------ load / save

    /**
     * Reads the data file. Returns false when the file does not exist or cannot be read,
     * in which case the caller starts with the default layout. Broken lines are skipped.
     */
    public boolean load() {
        if (!Files.exists(file)) {
            return false;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException e) {
            System.err.println("[DataStore] Cannot read " + file + ": " + e);
            return false;
        }
        long maxId = 0;
        List<WidgetLayout> loadedLayouts = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                maxId = Math.max(maxId, parseLine(line, loadedLayouts));
            } catch (RuntimeException ex) {
                System.err.println("[DataStore] Skipping broken line: " + line + " (" + ex.getMessage() + ")");
            }
        }
        layouts = loadedLayouts;
        nextId = maxId + 1;
        return true;
    }

    /** Parses one line and returns its id (0 if the line has no id). */
    private long parseLine(String line, List<WidgetLayout> loadedLayouts) {
        String[] f = line.split("\\|", -1);
        switch (f[0]) {
            case "SETTING": {
                if (f[1].equals("board")) {
                    String[] xy = f[2].split(",");
                    boardLocation = new java.awt.Point(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
                    return 0;
                }
                if (f[1].equals("widgetOpacity")) {
                    widgetOpacity = Math.max(30, Math.min(100, Integer.parseInt(f[2].trim())));
                    return 0;
                }
                if (f[1].equals("cell")) {
                    String[] wh = f[2].split("x");
                    int w = Integer.parseInt(wh[0].trim());
                    int h = Integer.parseInt(wh[1].trim());
                    if (w >= 40 && w <= 400 && h >= 40 && h <= 400) {
                        cellW = w;
                        cellH = h;
                    }
                    return 0;
                }
                if (f[1].equals("gridOrigin")) {
                    String[] xy = f[2].split(",");
                    gridOrigin = new java.awt.Point(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
                    return 0;
                }
                if (f[1].equals("gridMatched")) {
                    gridMatched = Boolean.parseBoolean(f[2].trim());
                    return 0;
                }
                if (f[1].equals("focus")) {
                    String[] m = f[2].split(",");
                    focusStudyMinutes = Math.max(1, Math.min(180, Integer.parseInt(m[0].trim())));
                    focusBreakMinutes = Math.max(1, Math.min(60, Integer.parseInt(m[1].trim())));
                    return 0;
                }
                if (f[1].equals("focusDone")) {
                    String[] dc = f[2].split(",");
                    focusDate = LocalDate.parse(dc[0].trim());
                    focusDoneToday = Integer.parseInt(dc[1].trim());
                    return 0;
                }
                if (f[1].equals("addButton")) {
                    String[] xy = f[2].split(",");
                    addButtonPos = new java.awt.Point(Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim()));
                    return 0;
                }
                if (f[1].equals("style")) {
                    style = Theme.Style.valueOf(f[2].trim());
                    return 0;
                }
                if (f[1].equals("solidText")) {
                    solidText = Boolean.parseBoolean(f[2].trim());
                    return 0;
                }
                if (f[1].equals("boardSize")) {
                    String[] cr = f[2].split("x");
                    int c = Integer.parseInt(cr[0].trim());
                    int r = Integer.parseInt(cr[1].trim());
                    if (c >= 2 && c <= 30 && r >= 3 && r <= 20) { // timetable needs 3 rows
                        setBoardSize(c, r);
                    }
                    return 0;
                }
                int v = Integer.parseInt(f[2]);
                if (f[1].equals("timetableStart") && v >= 0 && v < 24) {
                    timetableStartHour = v;
                } else if (f[1].equals("timetableEnd") && v > 0 && v <= 24) {
                    timetableEndHour = v;
                }
                if (timetableEndHour <= timetableStartHour) {
                    timetableStartHour = 0;
                    timetableEndHour = 24;
                }
                return 0;
            }
            case "EVENT": {
                long id = Long.parseLong(f[1]);
                int s = Times.parseHm(f[4]);
                int e = Times.parseHm(f[5]);
                if (e <= s) {
                    throw new IllegalArgumentException("end before start");
                }
                events.add(new Event(id, unescape(f[2]), LocalDate.parse(f[3]), s, e, Boolean.parseBoolean(f[6])));
                return id;
            }
            case "TODO": {
                long id = Long.parseLong(f[1]);
                todos.add(new Todo(id, unescape(f[2]), LocalDate.parse(f[3]), Boolean.parseBoolean(f[4])));
                return id;
            }
            case "CHECK": {
                long id = Long.parseLong(f[1]);
                checklist.add(new ChecklistItem(id, unescape(f[2]), LocalDate.parse(f[3]), Boolean.parseBoolean(f[4])));
                return id;
            }
            case "LAYOUT": {
                loadedLayouts.add(new WidgetLayout(WidgetType.valueOf(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5])));
                return 0;
            }
            default:
                throw new IllegalArgumentException("unknown kind " + f[0]);
        }
    }

    /** Writes everything to the data file (first to a temp file, then replaces the old file). */
    public void save() {
        if (layoutSupplier != null) {
            layouts = new ArrayList<>(layoutSupplier.get());
        }
        LocalDate oldest = LocalDate.now().minusDays(CHECKLIST_KEEP_DAYS);
        checklist.removeIf(item -> item.getDate().isBefore(oldest));

        List<String> out = new ArrayList<>();
        out.add("# Desktop Planner data - one item per line, fields separated by |");
        out.add("SETTING|timetableStart|" + timetableStartHour);
        out.add("SETTING|timetableEnd|" + timetableEndHour);
        out.add("SETTING|widgetOpacity|" + widgetOpacity);
        out.add("SETTING|solidText|" + solidText);
        out.add("SETTING|style|" + style.name());
        out.add("SETTING|focus|" + focusStudyMinutes + "," + focusBreakMinutes);
        out.add("SETTING|focusDone|" + focusDate + "," + focusDoneToday);
        if (addButtonPos != null) {
            out.add("SETTING|addButton|" + addButtonPos.x + "," + addButtonPos.y);
        }
        out.add("SETTING|cell|" + cellW + "x" + cellH);
        out.add("SETTING|gridMatched|" + gridMatched);
        out.add("SETTING|gridOrigin|" + gridOrigin.x + "," + gridOrigin.y);
        if (boardSizeSaved) {
            out.add("SETTING|boardSize|" + boardCols + "x" + boardRows);
        }
        if (boardLocation != null) {
            out.add("SETTING|board|" + boardLocation.x + "," + boardLocation.y);
        }
        for (Event e : events) {
            out.add("EVENT|" + e.getId() + "|" + escape(e.getTitle()) + "|" + e.getDate() + "|"
                    + Times.hm(e.getStartMin()) + "|" + Times.hm(e.getEndMin()) + "|" + e.isImportant());
        }
        for (Todo t : todos) {
            out.add("TODO|" + t.getId() + "|" + escape(t.getTitle()) + "|" + t.getDue() + "|" + t.isDone());
        }
        for (ChecklistItem c : checklist) {
            out.add("CHECK|" + c.getId() + "|" + escape(c.getTitle()) + "|" + c.getDate() + "|" + c.isDone());
        }
        for (WidgetLayout l : layouts) {
            out.add("LAYOUT|" + l.getType().name() + "|" + l.getCol() + "|" + l.getRow() + "|"
                    + l.getCols() + "|" + l.getRows());
        }

        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = dir.resolve(file.getFileName() + ".tmp");
            Files.write(tmp, out, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[DataStore] Cannot save " + file + ": " + e);
        }
    }

    /** '|' and line breaks would break the file format, so they are escaped. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '|': sb.append("\\p"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'p' ? '|' : n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
