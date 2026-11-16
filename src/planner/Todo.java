package planner;

import java.time.LocalDate;

/**
 * A one-time task with a due date. Shown by the To-do widget.
 */
public class Todo {

    private final long id;
    private String title;
    private LocalDate due;
    private boolean done;

    public Todo(long id, String title, LocalDate due, boolean done) {
        this.id = id;
        this.title = title;
        this.due = due;
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

    public LocalDate getDue() {
        return due;
    }

    public void setDue(LocalDate due) {
        this.due = due;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}
