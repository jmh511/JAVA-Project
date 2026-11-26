package planner;

/**
 * The widget kinds with their title, minimum size and default size (in cells).
 */
public enum WidgetType {
    CALENDAR("Calendar", 2, 2, 3, 3),
    TIMETABLE("Today", 2, 3, 3, 6),
    HIGHLIGHTS("Highlights", 2, 1, 3, 2),
    CHECKLIST("Checklist", 1, 2, 3, 3),
    TODO("To-do", 1, 2, 3, 3),
    FOCUS("Focus", 2, 2, 3, 3),
    SYSTEM("System", 2, 2, 3, 3),
    MEMO("Memo", 1, 1, 2, 2);

    private final String title;
    private final int minCols;
    private final int minRows;
    private final int defaultCols;
    private final int defaultRows;

    WidgetType(String title, int minCols, int minRows, int defaultCols, int defaultRows) {
        this.title = title;
        this.minCols = minCols;
        this.minRows = minRows;
        this.defaultCols = defaultCols;
        this.defaultRows = defaultRows;
    }

    public String getTitle() {
        return title;
    }

    public int getMinCols() {
        return minCols;
    }

    public int getMinRows() {
        return minRows;
    }

    public int getDefaultCols() {
        return defaultCols;
    }

    public int getDefaultRows() {
        return defaultRows;
    }

    /** True for widgets that keep their own content, so several can be on the board at once. */
    public boolean allowsMany() {
        return this == MEMO;
    }
}
