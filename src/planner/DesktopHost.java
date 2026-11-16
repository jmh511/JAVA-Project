package planner;

import javax.swing.JFrame;

/**
 * Platform code that attaches the board window to the desktop.
 * The Windows implementation lives in planner.win.Win32Desktop and is loaded by name,
 * so the main program still compiles and runs on JDKs that cannot build it.
 */
public interface DesktopHost {

    /** Attaches the (already visible) frame to the desktop. Throws with a reason on failure. */
    void attach(JFrame frame) throws Exception;

    /** Returns false if the attachment was lost (for example, Explorer was restarted). */
    boolean stillAttached();

    /** Puts the board back under all other windows, just above the desktop. */
    void sendToBack();

    /** Raises the board above other windows (without activating it). */
    void bringForward();

    /** Screen bounds of the desktop icons (in Java screen coordinates). Empty if unknown. */
    java.util.List<java.awt.Rectangle> desktopIconBounds();
}
