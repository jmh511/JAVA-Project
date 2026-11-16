package planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Shows the events of one day (opened by clicking a date in the calendar).
 */
public class DayDialog extends JDialog implements DataStore.Listener {

    private final DataStore store;
    private final LocalDate date;
    private final DefaultListModel<Event> model = new DefaultListModel<>();
    private final JList<Event> list = new JList<>(model);

    public static void show(Component parent, DataStore store, LocalDate date) {
        new DayDialog(SwingUtilities.getWindowAncestor(parent), store, date).setVisible(true);
    }

    private DayDialog(Window owner, DataStore store, LocalDate date) {
        super(owner, date.format(Times.DOW_MD) + ", " + date.getYear(), ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.date = date;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean selected, boolean focus) {
                Event e = (Event) value;
                String text = e.timeRange() + "   " + e.getTitle() + (e.isImportant() ? "   (important)" : "");
                JLabel c = (JLabel) super.getListCellRendererComponent(l, text, index, selected, focus);
                c.setBorder(new EmptyBorder(4, 6, 4, 6));
                return c;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    EventDialog.showEdit(DayDialog.this, store, list.getSelectedValue());
                }
            }
        });

        JButton add = new JButton("Add event");
        JButton edit = new JButton("Edit");
        JButton close = new JButton("Close");
        add.addActionListener(e -> EventDialog.showAdd(this, store, date,
                date.equals(LocalDate.now()) ? Times.nextHalfHour() : 9 * 60));
        edit.addActionListener(e -> {
            if (list.getSelectedValue() != null) {
                EventDialog.showEdit(this, store, list.getSelectedValue());
            }
        });
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(add);
        buttons.add(edit);
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new java.awt.Dimension(340, 200));
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        store.addListener(this);
        reload();
        pack();
        setLocationRelativeTo(owner);
    }

    private void reload() {
        model.clear();
        for (Event e : store.eventsOn(date)) {
            model.addElement(e);
        }
    }

    @Override
    public void dataChanged() {
        reload();
    }

    @Override
    public void dispose() {
        store.removeListener(this);
        super.dispose();
    }
}
