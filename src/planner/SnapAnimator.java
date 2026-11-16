package planner;

import java.awt.Rectangle;
import java.util.function.Consumer;

import javax.swing.Timer;

/**
 * Short "slide into place" animation used when something is released after dragging:
 * moves from the current rectangle to the snapped one, fast at first and slowing down
 * at the end (ease-out). Runs on the Swing timer, so it is safe to touch components.
 */
public final class SnapAnimator {

    /** Length of the snap animation in milliseconds. */
    public static final int DURATION_MS = 150;

    private SnapAnimator() {
    }

    /**
     * Calls apply with in-between rectangles for about DURATION_MS, ending exactly at 'to',
     * then calls done once.
     */
    public static void animate(Rectangle from, Rectangle to, Consumer<Rectangle> apply, Runnable done) {
        if (from.equals(to)) {
            apply.accept(new Rectangle(to));
            done.run();
            return;
        }
        long start = System.nanoTime();
        Timer timer = new Timer(15, null);
        timer.addActionListener(e -> {
            double t = Math.min(1.0, (System.nanoTime() - start) / 1_000_000.0 / DURATION_MS);
            double k = 1 - Math.pow(1 - t, 3); // ease-out cubic
            apply.accept(new Rectangle(
                    lerp(from.x, to.x, k), lerp(from.y, to.y, k),
                    lerp(from.width, to.width, k), lerp(from.height, to.height, k)));
            if (t >= 1.0) {
                timer.stop();
                done.run();
            }
        });
        timer.start();
    }

    private static int lerp(int a, int b, double k) {
        return (int) Math.round(a + (b - a) * k);
    }
}
