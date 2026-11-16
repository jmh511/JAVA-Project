package planner;

import java.time.LocalDate;

/**
 * A scheduled event on one day. Shown by Calendar, Today (timetable) and Highlights.
 */
public class Event {

    private final long id;
    private String title;
    private LocalDate date;
    private int startMin;
    private int endMin;
    private boolean important;

    public Event(long id, String title, LocalDate date, int startMin, int endMin, boolean important) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.startMin = startMin;
        this.endMin = endMin;
        this.important = important;
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

    public int getStartMin() {
        return startMin;
    }

    public void setStartMin(int startMin) {
        this.startMin = startMin;
    }

    public int getEndMin() {
        return endMin;
    }

    public void setEndMin(int endMin) {
        this.endMin = endMin;
    }

    public boolean isImportant() {
        return important;
    }

    public void setImportant(boolean important) {
        this.important = important;
    }

    public String timeRange() {
        return Times.hm(startMin) + "-" + Times.hm(endMin);
    }
}
