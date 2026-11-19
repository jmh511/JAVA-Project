package planner;

import java.awt.Component;
import java.awt.Window;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/**
 * Sets the focus timer's study and break length (minutes).
 */
public class FocusSettingsDialog extends FormDialog {

    private final DataStore store;
    private final JSpinner study;
    private final JSpinner rest;

    public static void show(Component parent, DataStore store) {
        new FocusSettingsDialog(SwingUtilities.getWindowAncestor(parent), store).open();
    }

    private FocusSettingsDialog(Window owner, DataStore store) {
        super(owner, "Focus timer");
        this.store = store;
        study = new JSpinner(new SpinnerNumberModel(store.getFocusStudyMinutes(), 1, 180, 5));
        rest = new JSpinner(new SpinnerNumberModel(store.getFocusBreakMinutes(), 1, 60, 1));
        addRow("Study (minutes)", study);
        addRow("Break (minutes)", rest);
    }

    @Override
    protected void onSave() {
        store.setFocusMinutes((Integer) study.getValue(), (Integer) rest.getValue());
        FocusTimer.get(store).settingsChanged();
        dispose();
    }
}
