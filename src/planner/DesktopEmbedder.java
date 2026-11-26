package planner;

import java.util.Locale;

import javax.swing.JFrame;

/**
 * Attaches the board window to the desktop (2nd goal of the project).
 *
 * The Windows code (planner.win.Win32Desktop) uses the JDK foreign function API, which needs
 * JDK 22 or newer. It is compiled separately and loaded by name here, so when it is missing
 * (older JDK, other OS) or fails, the program simply keeps running as a normal window.
 * All embedding code stays behind this class; the rest of the program does not depend on it.
 */
public final class DesktopEmbedder {

    private static final String IMPL_CLASS = "planner.win.Win32Desktop";

    private static String lastError = "";
    private static DesktopHost host;

    private DesktopEmbedder() {
    }

    /** True if desktop mode can be tried on this machine (Windows and the native part is available). */
    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("windows")) {
            lastError = "Desktop mode needs Windows (current OS: " + os + ").";
            return false;
        }
        if (!"false".equals(System.getProperty("planner.desktop")) && loadHost() != null) {
            return true;
        }
        if (lastError.isEmpty()) {
            lastError = "Desktop mode was turned off (-Dplanner.desktop=false).";
        }
        return false;
    }

    private static DesktopHost loadHost() {
        if (host != null) {
            return host;
        }
        try {
            host = (DesktopHost) Class.forName(IMPL_CLASS).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException | LinkageError e) {
            lastError = "Desktop mode needs JDK 22 or newer (native part not available: " + e + ").";
        } catch (ReflectiveOperationException | RuntimeException e) {
            lastError = "Desktop mode could not start: " + e;
        }
        return host;
    }

    /** Returns true only when the window was attached to the desktop. Never throws. */
    public static boolean tryEmbed(JFrame frame) {
        if (!isSupported()) {
            return false;
        }
        try {
            host.attach(frame);
            return true;
        } catch (Exception | LinkageError e) {
            lastError = "Desktop embedding failed: " + e.getMessage();
            return false;
        }
    }

    /** Called regularly while embedded. False means the board lost its place on the desktop. */
    public static boolean checkAttached() {
        return host != null && host.stillAttached();
    }

    public static void sendToBack() {
        if (host != null) {
            host.sendToBack();
        }
    }

    public static void bringForward() {
        if (host != null) {
            host.bringForward();
        }
    }

    /** See DesktopHost.beginKeyboardInput. Never throws. */
    public static boolean beginKeyboardInput() {
        try {
            return host != null && host.beginKeyboardInput();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    public static void endKeyboardInput() {
        if (host != null) {
            host.endKeyboardInput();
        }
    }

    /** Desktop icon rectangles, so the board can stay off the user's files. Never throws. */
    public static java.util.List<java.awt.Rectangle> iconBounds() {
        if (host == null) {
            return java.util.Collections.emptyList();
        }
        try {
            return host.desktopIconBounds();
        } catch (RuntimeException | LinkageError e) {
            return java.util.Collections.emptyList();
        }
    }

    public static String getLastError() {
        return lastError;
    }
}
