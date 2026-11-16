package planner;

import java.awt.Component;
import java.awt.Window;
import java.time.LocalDate;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Add / edit an event. Times can only be picked in 30 minute steps.
 */
public class EventDialog extends FormDialog {

    private final DataStore store;
    private final Event editing;

    private final JTextField titleField = new JTextField(20);
    private final DateField dateField;
    private final JComboBox<String> startBox = new JComboBox<>(Times.halfHours(0, Times.DAY_MINUTES - 30));
    private final JComboBox<String> endBox = new JComboBox<>(Times.halfHours(30, Times.DAY_MINUTES));
    private final JCheckBox importantBox = new JCheckBox("Important (show in Highlights)");

    public static void showAdd(Component parent, DataStore store, LocalDate date, int startMin) {
        int start = Math.max(0, Math.min(startMin / 30 * 30, Times.DAY_MINUTES - 30));
        int end = Math.min(start + 60, Times.DAY_MINUTES);
        new EventDialog(SwingUtilities.getWindowAncestor(parent), store, null, date, start, end, false, "").open();
    }

    public static void showEdit(Component parent, DataStore store, Event e) {
        new EventDialog(SwingUtilities.getWindowAncestor(parent), store, e, e.getDate(),
                e.getStartMin(), e.getEndMin(), e.isImportant(), e.getTitle()).open();
    }

    private EventDialog(Window owner, DataStore store, Event editing, LocalDate date,
                        int start, int end, boolean important, String title) {
        super(owner, editing == null ? "Add event" : "Edit event");
        this.store = store;
        this.editing = editing;
        this.dateField = new DateField(date);

        titleField.setText(title);
        startBox.setSelectedItem(Times.hm(start));
        endBox.setSelectedItem(Times.hm(end));
        importantBox.setSelected(important);

        // Keep the end time after the start time when the start changes.
        startBox.addActionListener(e -> {
            int s = Times.parseHm((String) startBox.getSelectedItem());
            int en = Times.parseHm((String) endBox.getSelectedItem());
            if (en <= s) {
                endBox.setSelectedItem(Times.hm(Math.min(s + 60, Times.DAY_MINUTES)));
            }
        });

        addRow("Title", titleField);
        addRow("Date", dateField);
        addRow("Start", startBox);
        addRow("End", endBox);
        addRow(null, importantBox);

        if (editing != null) {
            JButton delete = new JButton("Delete");
            delete.addActionListener(e -> {
                int answer = JOptionPane.showConfirmDialog(this, "Delete \"" + editing.getTitle() + "\"?",
                        "Delete event", JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) {
                    store.removeEvent(editing);
                    dispose();
                }
            });
            addLeftButton(delete);
        }
    }

    @Override
    protected void onSave() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("Please enter a title.");
            return;
        }
        int s = Times.parseHm((String) startBox.getSelectedItem());
        int e = Times.parseHm((String) endBox.getSelectedItem());
        if (e <= s) {
            showError("End time must be later than start time.");
            return;
        }
        LocalDate date = dateField.getDate();
        if (editing == null) {
            store.addEvent(title, date, s, e, importantBox.isSelected());
        } else {
            store.updateEvent(editing, title, date, s, e, importantBox.isSelected());
        }
        dispose();
    }
}
