package planner;

/**
 * Saved position of one widget: type, top-left cell and size in cells.
 */
public class WidgetLayout {

    private final WidgetType type;
    private final int col;
    private final int row;
    private final int cols;
    private final int rows;

    public WidgetLayout(WidgetType type, int col, int row, int cols, int rows) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.cols = cols;
        this.rows = rows;
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
}
