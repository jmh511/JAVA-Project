package planner;

import java.awt.Component;
import java.awt.Window;
import java.time.LocalDate;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Add / edit a to-do (title and due date).
 */
public class TodoDialog extends FormDialog {

    private final DataStore store;
    private final Todo editing;
    private final JTextField titleField = new JTextField(20);
    private final DateField dueField;

    public static void showAdd(Component parent, DataStore store) {
        new TodoDialog(SwingUtilities.getWindowAncestor(parent), store, null).open();
    }

    public static void showEdit(Component parent, DataStore store, Todo todo) {
        new TodoDialog(SwingUtilities.getWindowAncestor(parent), store, todo).open();
    }

    private TodoDialog(Window owner, DataStore store, Todo editing) {
        super(owner, editing == null ? "Add to-do" : "Edit to-do");
        this.store = store;
        this.editing = editing;
        dueField = new DateField(editing == null ? LocalDate.now().plusDays(1) : editing.getDue());
        if (editing != null) {
            titleField.setText(editing.getTitle());
        }
        addRow("Title", titleField);
        addRow("Due date", dueField);
    }

    @Override
    protected void onSave() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            showError("Please enter a title.");
            return;
        }
        if (editing == null) {
            store.addTodo(title, dueField.getDate());
        } else {
            store.updateTodo(editing, title, dueField.getDate());
        }
        dispose();
    }
}
