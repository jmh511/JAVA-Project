package planner;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import javax.swing.JOptionPane;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

/**
 * Desktop Planner - a grid board of planner widgets for the Windows desktop.
 * Data is saved to data/planner.txt (relative to the folder the program is started from).
 */
public class Main {

    /** Kept open while the program runs so that a second copy can detect it. */
    private static FileChannel lockChannel;

    public static void main(String[] args) {
        if (!acquireSingleInstanceLock()) {
            JOptionPane.showMessageDialog(null, "Desktop Planner is already running.\n"
                    + "Use the tray icon (or right-click the board) to show or exit it.",
                    "Desktop Planner", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }
        SwingUtilities.invokeLater(Main::start);
    }

    /** Two copies would overwrite each other's data file, so only one may run. */
    private static boolean acquireSingleInstanceLock() {
        try {
            Path dir = Paths.get("data");
            Files.createDirectories(dir);
            lockChannel = FileChannel.open(dir.resolve(".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            return lockChannel.tryLock() != null;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException e) {
            System.err.println("[Main] Single-instance check skipped: " + e);
            return true; // do not block starting because of this check
        }
    }

    private static void start() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("[Main] Using the default look and feel: " + e.getMessage());
        }
        ToolTipManager.sharedInstance().setInitialDelay(400);
        RepaintManager.setCurrentManager(new WidgetRepaintManager()); // needed for see-through widgets

        DataStore store = new DataStore(Paths.get("data", "planner.txt"));
        boolean loaded = store.load();

        BoardFrame frame = new BoardFrame(store);
        if (loaded) {
            frame.getBoard().applyLayouts(store.getLayouts());
        } else {
            frame.getBoard().applyDefaultLayout(); // first run, or the file could not be read
        }
        frame.setVisible(true);
        frame.startDesktopMode();
        PipWindow.restore(frame.getBoard()); // pop-out window from last time, if any
    }
}
