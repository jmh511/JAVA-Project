package planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

/**
 * Daily check items. The number of days shown depends on the width:
 * 1-2 cells = today, 3-6 cells = yesterday/today/tomorrow, 7+ cells = this week (Sun-Sat).
 */
public class ChecklistWidget extends WidgetPanel {

    private int days = 1;

    public ChecklistWidget(DataStore store) {
        super(WidgetType.CHECKLIST, store);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        days = cols <= 2 ? 1 : cols <= 6 ? 3 : 7;
        refresh();
    }

    /** Dates to show, based on today (so after midnight everything moves one column to the left). */
    private List<LocalDate> shownDates() {
        LocalDate today = LocalDate.now();
        List<LocalDate> dates = new ArrayList<>();
        if (days == 1) {
            dates.add(today);
        } else if (days == 3) {
            for (int i = -1; i <= 1; i++) {
                dates.add(today.plusDays(i));
            }
        } else {
            LocalDate sunday = Times.weekStart(today);
            for (int i = 0; i < 7; i++) {
                dates.add(sunday.plusDays(i));
            }
        }
        return dates;
    }

    @Override
    protected void refresh() {
        titleLabel.setText(days == 7 ? "Checklist  this week" : "Checklist");
        body.removeAll();
        JPanel row = new JPanel(new GridLayout(1, days, 4, 0));
        row.setOpaque(false);
        for (LocalDate d : shownDates()) {
            row.add(new DayColumn(d));
        }
        body.add(row);
    }

    private String headerFor(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (days == 7) {
            return d.format(Times.DOW) + " " + d.getDayOfMonth();
        }
        if (d.equals(today)) {
            return "Today " + d.format(Times.MD);
        }
        return (d.isBefore(today) ? "Yesterday " : "Tomorrow ") + d.format(Times.MD);
    }

    /** One date: header, its items, and an add button. */
    private class DayColumn extends JPanel {
        private final boolean today;

        DayColumn(LocalDate date) {
            super(new BorderLayout(0, 2));
            today = date.equals(LocalDate.now());
            setOpaque(false);
            setBorder(new EmptyBorder(3, 4, 2, 2));

            JLabel header = Theme.label(headerFor(date), today ? Theme.SMALL.deriveFont(java.awt.Font.BOLD) : Theme.SMALL,
                    today ? Theme.ACCENT : Theme.TEXT_MUTED);
            add(header, BorderLayout.NORTH);

            JPanel items = new JPanel();
            items.setOpaque(false);
            items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
            for (ChecklistItem item : store.checklistOn(date)) {
                items.add(createItem(item));
            }
            JPanel itemsHolder = new WidthTrackingPanel(new BorderLayout());
            itemsHolder.add(items, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(itemsHolder);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(10);
            add(scroll, BorderLayout.CENTER);

            JButton addButton = Theme.flatButton(days == 7 ? "+ Add" : "+ Add item");
            addButton.setHorizontalAlignment(JButton.LEFT);
            addButton.addActionListener(e -> {
                String title = JOptionPane.showInputDialog(ChecklistWidget.this,
                        "New item for " + date.format(Times.DOW_MD) + ":", "Add checklist item",
                        JOptionPane.PLAIN_MESSAGE);
                if (title != null && !title.trim().isEmpty()) {
                    store.addChecklistItem(title.trim(), date);
                }
            });
            add(addButton, BorderLayout.SOUTH);
        }

        private Component createItem(ChecklistItem item) {
            JCheckBox cb = new JCheckBox(item.getTitle(), item.isDone());
            cb.setOpaque(false);
            cb.setFocusPainted(false);
            cb.setFont(Theme.SMALL);
            cb.setForeground(item.isDone() ? Theme.TEXT_MUTED : Theme.TEXT);
            cb.setToolTipText(item.getTitle());
            cb.setBorder(new EmptyBorder(1, 0, 1, 0));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            cb.addActionListener(e -> store.setChecklistDone(item, cb.isSelected()));

            JPopupMenu menu = new JPopupMenu();
            JMenuItem delete = new JMenuItem("Delete item");
            delete.addActionListener(e -> store.removeChecklistItem(item));
            menu.add(delete);
            cb.setComponentPopupMenu(menu);
            return cb;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (today && days > 1) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.ACCENT_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}
