package planner;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

/**
 * One-time tasks with a due date, sorted by due date. Finished tasks move to the bottom.
 * Small sizes show only titles; 2x3 cells or more also show the due date and D-day.
 */
public class TodoWidget extends WidgetPanel {

    private boolean detailed;

    public TodoWidget(DataStore store) {
        super(WidgetType.TODO, store);
        JButton add = Theme.flatButton("+");
        add.setFont(Theme.TITLE);
        add.setToolTipText("Add a to-do");
        add.addActionListener(e -> TodoDialog.showAdd(this, store));
        addHeaderTool(add);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        detailed = cols >= 2 && rows >= 3;
        refresh();
    }

    @Override
    public List<JMenuItem> createMenuItems() {
        JMenuItem add = new JMenuItem("Add to-do...");
        add.addActionListener(e -> TodoDialog.showAdd(this, store));
        List<JMenuItem> items = new ArrayList<>();
        items.add(add);
        return items;
    }

    @Override
    protected void refresh() {
        body.removeAll();
        List<Todo> todos = store.getTodos();
        if (todos.isEmpty()) {
            body.add(Theme.label("No to-dos. Click + to add.", Theme.SMALL, Theme.TEXT_MUTED), BorderLayout.NORTH);
            return;
        }
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (Todo t : todos) {
            list.add(createRow(t));
        }
        JPanel holder = new WidthTrackingPanel(new BorderLayout());
        holder.add(list, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(10);
        body.add(scroll);
    }

    private JPanel createRow(Todo t) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(1, 0, 1, 2));

        JCheckBox cb = new JCheckBox(t.getTitle(), t.isDone());
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setBorder(new EmptyBorder(0, 0, 0, 0));
        Font font = Theme.SMALL;
        if (t.isDone()) {
            font = font.deriveFont(Collections.singletonMap(TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON));
        }
        cb.setFont(font);
        cb.setForeground(t.isDone() ? Theme.TEXT_MUTED : Theme.TEXT);
        cb.setToolTipText(t.getTitle() + "  (due " + t.getDue().format(Times.DOW_MD) + ")");
        cb.addActionListener(e -> store.setTodoDone(t, cb.isSelected()));
        row.add(cb, BorderLayout.CENTER);

        if (detailed) {
            long left = ChronoUnit.DAYS.between(LocalDate.now(), t.getDue());
            String dday = t.isDone() ? "done" : left == 0 ? "D-day" : left > 0 ? "D-" + left : "D+" + (-left);
            JLabel due = Theme.label(t.getDue().format(Times.MD) + "  " + dday, Theme.TINY,
                    !t.isDone() && left < 0 ? Theme.DANGER
                            : !t.isDone() && left <= 1 ? Theme.IMPORTANT : Theme.TEXT_MUTED);
            due.setBorder(new EmptyBorder(0, 0, 0, 4));
            row.add(due, BorderLayout.EAST);
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem edit = new JMenuItem("Edit...");
        edit.addActionListener(e -> TodoDialog.showEdit(this, store, t));
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> store.removeTodo(t));
        menu.add(edit);
        menu.add(delete);
        cb.setComponentPopupMenu(menu);
        row.setComponentPopupMenu(menu);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }
}
