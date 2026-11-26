package planner;

/**
 * One sticky note: its text, background color name (see Theme.memoColor) and the time
 * of the last change. Each memo widget shows one memo, so memos are linked by id.
 */
public class Memo {

    private final long id;
    private String text;
    private String color;
    private long updatedAt;

    public Memo(long id, String text, String color, long updatedAt) {
        this.id = id;
        this.text = text;
        this.color = color;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    void setText(String text) {
        this.text = text;
        updatedAt = System.currentTimeMillis();
    }

    public String getColor() {
        return color;
    }

    void setColor(String color) {
        this.color = color;
        updatedAt = System.currentTimeMillis();
    }

    /** Milliseconds since 1970 (System.currentTimeMillis) of the last change. */
    public long getUpdatedAt() {
        return updatedAt;
    }
}
