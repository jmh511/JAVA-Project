package planner;

/**
 * Saved position of one widget: type, top-left cell and size in cells.
 * 'ref' links widgets that own their data (a memo) to it; it is 0 for all other widgets.
 */
public class WidgetLayout {

    private final WidgetType type;
    private final int col;
    private final int row;
    private final int cols;
    private final int rows;
    private final long ref;

    public WidgetLayout(WidgetType type, int col, int row, int cols, int rows) {
        this(type, col, row, cols, rows, 0);
    }

    public WidgetLayout(WidgetType type, int col, int row, int cols, int rows, long ref) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.cols = cols;
        this.rows = rows;
        this.ref = ref;
    }

    public WidgetType getType() {
        return type;
    }

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /** Id of the widget's own data (memo id), or 0. */
    public long getRef() {
        return ref;
    }
}
