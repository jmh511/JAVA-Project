package planner;

import java.awt.Component;
import java.awt.Window;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;

/**
 * Chooses the visible hour range of the Today (timetable) widget.
 */
public class TimetableSettingsDialog extends FormDialog {

    private final DataStore store;
    private final JComboBox<String> startBox = new JComboBox<>();
    private final JComboBox<String> endBox = new JComboBox<>();

    public static void show(Component parent, DataStore store) {
        new TimetableSettingsDialog(SwingUtilities.getWindowAncestor(parent), store).open();
    }

    private TimetableSettingsDialog(Window owner, DataStore store) {
        super(owner, "Timetable settings");
        this.store = store;
        for (int h = 0; h < 24; h++) {
            startBox.addItem(Times.hm(h * 60));
        }
        for (int h = 1; h <= 24; h++) {
            endBox.addItem(Times.hm(h * 60));
        }
        startBox.setSelectedItem(Times.hm(store.getTimetableStartHour() * 60));
        endBox.setSelectedItem(Times.hm(store.getTimetableEndHour() * 60));
        addRow("Show from", startBox);
        addRow("Show until", endBox);
    }

    @Override
    protected void onSave() {
        int start = Times.parseHm((String) startBox.getSelectedItem()) / 60;
        int end = Times.parseHm((String) endBox.getSelectedItem()) / 60;
        if (end <= start) {
            showError("End time must be later than start time.");
            return;
        }
        store.setTimetableRange(start, end);
        dispose();
    }
}
