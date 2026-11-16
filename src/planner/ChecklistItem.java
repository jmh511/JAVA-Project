package planner;

import java.time.LocalDate;

/**
 * A daily check item that belongs to one date. Shown by the Checklist widget.
 */
public class ChecklistItem {

    private final long id;
    private String title;
    private LocalDate date;
    private boolean done;

    public ChecklistItem(long id, String title, LocalDate date, boolean done) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.done = done;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
