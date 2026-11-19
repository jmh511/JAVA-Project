package planner;

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.Timer;

/**
 * Study / break timer (Pomodoro style). One timer for the whole program, kept outside the
 * widget so it keeps running when the widget is rebuilt (style change) or removed for a while.
 * Times are counted against the clock (end time), so the timer stays exact even if the
 * screen updates are late.
 */
public final class FocusTimer {

    public enum Phase {
        STUDY("Study"), BREAK("Break");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static FocusTimer instance;

    /** Shows a short system notification (set by BoardFrame: tray balloon). */
    public static BiConsumer<String, String> notifier = (title, text) -> { };

    public static FocusTimer get(DataStore store) {
        if (instance == null) {
            instance = new FocusTimer(store);
        }
        return instance;
    }

    private final DataStore store;
    private final List<Runnable> listeners = new ArrayList<>();
    private final Timer ticker = new Timer(250, e -> tick());

    private Phase phase = Phase.STUDY;
    private boolean running;
    private long endAt;        // clock time when the phase ends (while running)
    private long remainingMs;  // time left (while paused)

    private FocusTimer(DataStore store) {
        this.store = store;
        remainingMs = phaseLength();
    }

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void fire() {
        for (Runnable r : new ArrayList<>(listeners)) {
            r.run();
        }
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isRunning() {
        return running;
    }

    public long phaseLength() {
        int minutes = phase == Phase.STUDY ? store.getFocusStudyMinutes() : store.getFocusBreakMinutes();
        return minutes * 60_000L;
    }

    public long remaining() {
        return running ? Math.max(0, endAt - System.currentTimeMillis()) : remainingMs;
    }

    /** 0.0 at the start of a phase, 1.0 at its end. */
    public double progress() {
        return 1.0 - remaining() / (double) phaseLength();
    }

    /** True when nothing has been counted yet in this phase (not started, or reset). */
    public boolean isFresh() {
        return !running && remainingMs == phaseLength();
    }

    public void start() {
        if (running) {
            return;
        }
        endAt = System.currentTimeMillis() + remainingMs;
        running = true;
        ticker.start();
        fire();
    }

    public void pause() {
        if (!running) {
            return;
        }
        remainingMs = remaining();
        running = false;
        ticker.stop();
        fire();
    }

    public void toggle() {
        if (running) {
            pause();
        } else {
            start();
        }
    }

    /** Back to a fresh study phase. */
    public void reset() {
        running = false;
        ticker.stop();
        phase = Phase.STUDY;
        remainingMs = phaseLength();
        fire();
    }

    /** Ends the current phase now (no session is counted). */
    public void skip() {
        nextPhase(false);
    }

    /** Study / break lengths were changed: a phase that has not started takes the new length. */
    public void settingsChanged() {
        if (!running) {
            remainingMs = phaseLength();
        }
        fire();
    }

    private void tick() {
        if (running && System.currentTimeMillis() >= endAt) {
            nextPhase(true);
        } else {
            fire();
        }
    }

    /** Switches study <-> break. When a phase really finished, the next one starts by itself. */
    private void nextPhase(boolean finished) {
        boolean wasStudy = phase == Phase.STUDY;
        if (finished && wasStudy) {
            store.addFocusSession();
        }
        phase = wasStudy ? Phase.BREAK : Phase.STUDY;
        remainingMs = phaseLength();
        if (running) {
            endAt = System.currentTimeMillis() + remainingMs;
        }
        if (finished) {
            Toolkit.getDefaultToolkit().beep();
            if (wasStudy) {
                notifier.accept("Study time is over", "Take a " + store.getFocusBreakMinutes() + "-minute break.");
            } else {
                notifier.accept("Break is over", "Back to studying for " + store.getFocusStudyMinutes() + " minutes.");
            }
        }
        fire();
    }

    /** "24:59" style text for a number of milliseconds (rounded up to whole seconds). */
    public static String format(long ms) {
        long s = (ms + 999) / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }
}
