package planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/**
 * One day at a glance (opened by clicking a date in the calendar):
 *  - header with the date and arrows to go to the previous / next day (also Left / Right keys);
 *  - the day's events as cards (click to edit, x to delete);
 *  - to-dos that are due that day (can be checked here);
 *  - a quick-add row at the bottom: title, start, end, important, Enter or Add.
 */
public class DayDialog extends JDialog implements DataStore.Listener {

    private static final DateTimeFormatter TITLE = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH);
    private static final int LIST_WIDTH = 380;
    private static final int LIST_HEIGHT = 230;

    private final DataStore store;
    private LocalDate date;

    private final JLabel dateLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel badge = new JLabel("", SwingConstants.CENTER);
    private final JPanel listPanel = new WidthTrackingPanel(null);

    private final JTextField titleField = Theme.hintField("New event title", 14);
    private final JComboBox<String> startBox = new JComboBox<>(Times.halfHours(0, Times.DAY_MINUTES - 30));
    private final JComboBox<String> endBox = new JComboBox<>(Times.halfHours(30, Times.DAY_MINUTES));
    private final JCheckBox importantBox = new JCheckBox("Important");
    private final JLabel errorLabel = Theme.label(" ", Theme.SMALL, Theme.DANGER);

    public static void show(Component parent, DataStore store, LocalDate date) {
        new DayDialog(SwingUtilities.getWindowAncestor(parent), store, date).setVisible(true);
    }

    private DayDialog(Window owner, DataStore store, LocalDate date) {
        super(owner, "Day", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.date = date;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.CARD_BG);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));
        root.add(createHeader(), BorderLayout.NORTH);
        root.add(createList(), BorderLayout.CENTER);
        root.add(createQuickAdd(), BorderLayout.SOUTH);
        setContentPane(root);

        bindKey(KeyEvent.VK_ESCAPE, "close", this::dispose);
        bindKey(KeyEvent.VK_LEFT, "prevDay", () -> moveDays(-1));
        bindKey(KeyEvent.VK_RIGHT, "nextDay", () -> moveDays(1));

        store.addListener(this);
        reload();
        resetQuickAdd();
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(titleField::requestFocusInWindow);
    }

    // ------------------------------------------------------------------ layout

    private JComponent createHeader() {
        JButton prev = arrowButton("<", "Previous day (Left key)");
        JButton next = arrowButton(">", "Next day (Right key)");
        prev.addActionListener(e -> moveDays(-1));
        next.addActionListener(e -> moveDays(1));

        dateLabel.setFont(Theme.TITLE.deriveFont(17f));
        dateLabel.setForeground(Theme.TEXT);
        badge.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        badge.setOpaque(true);
        badge.setBorder(new EmptyBorder(1, 8, 1, 8));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(dateLabel);
        center.add(Box.createVerticalStrut(3));
        center.add(badge);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(prev, BorderLayout.WEST);
        header.add(center, BorderLayout.CENTER);
        header.add(next, BorderLayout.EAST);
        return header;
    }

    private static JButton arrowButton(String text, String tip) {
        JButton b = Theme.flatButton(text);
        b.setFont(Theme.TITLE.deriveFont(16f));
        b.setToolTipText(tip);
        b.setPreferredSize(new Dimension(34, 34));
        return b;
    }

    private JComponent createList() {
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        JPanel holder = new WidthTrackingPanel(new BorderLayout());
        holder.add(listPanel, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        scroll.setPreferredSize(new Dimension(LIST_WIDTH, LIST_HEIGHT));
        return scroll;
    }

    private JComponent createQuickAdd() {
        startBox.setFont(Theme.BODY);
        endBox.setFont(Theme.BODY);
        importantBox.setFont(Theme.SMALL);
        importantBox.setForeground(Theme.TEXT_MUTED);
        importantBox.setOpaque(false);
        importantBox.setFocusPainted(false);

        // Keep the end time after the start time when the start changes.
        startBox.addActionListener(e -> {
            int s = Times.parseHm((String) startBox.getSelectedItem());
            int en = Times.parseHm((String) endBox.getSelectedItem());
            if (en <= s) {
                endBox.setSelectedItem(Times.hm(Math.min(s + 60, Times.DAY_MINUTES)));
            }
        });
        titleField.addActionListener(e -> addEvent()); // Enter
        titleField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                errorLabel.setText(" ");
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });
        JButton add = Theme.primaryButton("+ Add");
        add.addActionListener(e -> addEvent());

        JPanel row1 = new JPanel(new BorderLayout(6, 0));
        row1.setOpaque(false);
        JPanel times = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        times.setOpaque(false);
        times.add(startBox);
        times.add(Theme.label("-", Theme.BODY, Theme.TEXT_MUTED));
        times.add(endBox);
        row1.add(titleField, BorderLayout.CENTER);
        row1.add(times, BorderLayout.EAST);

        JPanel row2 = new JPanel(new BorderLayout());
        row2.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(importantBox);
        left.add(Box.createHorizontalStrut(10));
        left.add(errorLabel);
        row2.add(left, BorderLayout.CENTER);
        row2.add(add, BorderLayout.EAST);

        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setOpaque(false);
        box.setBorder(BorderCompat.top());
        box.add(row1, BorderLayout.NORTH);
        box.add(row2, BorderLayout.SOUTH);
        return box;
    }

    /** Hairline above the quick-add area, with some space below it. */
    private static final class BorderCompat {
        static javax.swing.border.Border top() {
            return javax.swing.BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, Theme.CARD_BORDER), new EmptyBorder(12, 0, 0, 0));
        }
    }

    private void bindKey(int key, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ------------------------------------------------------------------ content

    private void moveDays(int days) {
        date = date.plusDays(days);
        reload();
        resetQuickAdd();
    }

    private void reload() {
        LocalDate today = LocalDate.now();
        String title = date.format(TITLE) + (date.getYear() != today.getYear() ? ", " + date.getYear() : "");
        dateLabel.setText(title);
        setTitle(title);
        long diff = ChronoUnit.DAYS.between(today, date);
        if (diff == 0) {
            setBadge("Today", Theme.ACCENT_SOFT, Theme.ACCENT);
        } else if (diff == 1) {
            setBadge("Tomorrow", Theme.CARD_BORDER, Theme.TEXT);
        } else if (diff == -1) {
            setBadge("Yesterday", Theme.CARD_BORDER, Theme.TEXT);
        } else {
            setBadge(diff > 0 ? "in " + diff + " days" : (-diff) + " days ago", Theme.CARD_BORDER, Theme.TEXT);
        }

        listPanel.removeAll();
        listPanel.add(sectionLabel("Events"));
        List<Event> events = store.eventsOn(date);
        if (events.isEmpty()) {
            listPanel.add(muted("No events yet. Add one below."));
        }
        for (Event e : events) {
            listPanel.add(new EventCard(e));
            listPanel.add(Box.createVerticalStrut(6));
        }

        List<Todo> due = new ArrayList<>();
        for (Todo t : store.getTodos()) {
            if (t.getDue().equals(date)) {
                due.add(t);
            }
        }
        if (!due.isEmpty()) {
            listPanel.add(Box.createVerticalStrut(8));
            listPanel.add(sectionLabel("Due this day"));
            for (Todo t : due) {
                listPanel.add(todoRow(t));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private void setBadge(String text, Color bg, Color fg) {
        badge.setText(text);
        badge.setBackground(bg);
        badge.setForeground(fg);
    }

    private static JComponent sectionLabel(String text) {
        JLabel l = Theme.label(text, Theme.SMALL.deriveFont(Font.BOLD), Theme.TEXT_MUTED);
        l.setBorder(new EmptyBorder(2, 2, 6, 0));
        return leftAligned(l);
    }

    private static JComponent muted(String text) {
        JLabel l = Theme.label(text, Theme.BODY, Theme.TEXT_MUTED);
        l.setBorder(new EmptyBorder(4, 2, 4, 0));
        return leftAligned(l);
    }

    private static JComponent leftAligned(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private JComponent todoRow(Todo t) {
        JCheckBox cb = new JCheckBox(t.getTitle(), t.isDone());
        cb.setFont(Theme.BODY);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setForeground(t.isDone() ? Theme.TEXT_MUTED : Theme.TEXT);
        cb.addActionListener(e -> store.setTodoDone(t, cb.isSelected()));
        long left = ChronoUnit.DAYS.between(LocalDate.now(), t.getDue());
        String dday = t.isDone() ? "done" : left == 0 ? "D-day" : left > 0 ? "D-" + left : "D+" + (-left);
        JLabel d = Theme.label(dday, Theme.SMALL, !t.isDone() && left <= 0 ? Theme.DANGER : Theme.TEXT_MUTED);
        d.setBorder(new EmptyBorder(0, 0, 0, 6));
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(cb, BorderLayout.CENTER);
        row.add(d, BorderLayout.EAST);
        return leftAligned(row);
    }

    /** Defaults for the quick-add row: today = next half hour, other days = 09:00. */
    private void resetQuickAdd() {
        int start = date.equals(LocalDate.now()) ? Times.nextHalfHour() : 9 * 60;
        startBox.setSelectedItem(Times.hm(start));
        endBox.setSelectedItem(Times.hm(Math.min(start + 60, Times.DAY_MINUTES)));
        importantBox.setSelected(false);
        errorLabel.setText(" ");
    }

    private void addEvent() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            errorLabel.setText("Enter a title first.");
            titleField.requestFocusInWindow();
            return;
        }
        int s = Times.parseHm((String) startBox.getSelectedItem());
        int e = Times.parseHm((String) endBox.getSelectedItem());
        if (e <= s) {
            errorLabel.setText("End must be after start.");
            return;
        }
        store.addEvent(title, date, s, e, importantBox.isSelected());
        // Ready for the next one: starts where this one ended.
        titleField.setText("");
        int next = Math.min(e, Times.DAY_MINUTES - 30);
        startBox.setSelectedItem(Times.hm(next));
        endBox.setSelectedItem(Times.hm(Math.min(next + 60, Times.DAY_MINUTES)));
        importantBox.setSelected(false);
        errorLabel.setText(" ");
        titleField.requestFocusInWindow();
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

    // ------------------------------------------------------------------ event card

    /** One event, drawn like a block in the Today widget. Click to edit, x to delete. */
    private class EventCard extends JPanel {
        private final Event event;

        EventCard(Event event) {
            super(new BorderLayout(10, 0));
            this.event = event;
            setOpaque(false);
            setBorder(new EmptyBorder(8, 14, 8, 6));
            Color fg = event.isImportant() ? Theme.IMPORTANT : Theme.ACCENT;

            JLabel time = Theme.label(event.timeRange(), Theme.SMALL, fg);
            time.setPreferredSize(new Dimension(84, time.getPreferredSize().height));
            JLabel title = Theme.label(event.getTitle() + (event.isImportant() ? "  ★" : ""), Theme.BODY, Theme.TEXT);
            JButton delete = Theme.flatButton("x");
            delete.setForeground(Theme.TEXT_MUTED);
            delete.setToolTipText("Delete this event");
            delete.addActionListener(e -> {
                int answer = JOptionPane.showConfirmDialog(DayDialog.this, "Delete \"" + event.getTitle() + "\"?",
                        "Delete event", JOptionPane.YES_NO_OPTION);
                if (answer == JOptionPane.YES_OPTION) {
                    store.removeEvent(event);
                }
            });

            add(time, BorderLayout.WEST);
            add(title, BorderLayout.CENTER);
            add(delete, BorderLayout.EAST);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
            setToolTipText("Click to edit");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter open = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        EventDialog.showEdit(DayDialog.this, store, event);
                    }
                }
            };
            addMouseListener(open);
            time.addMouseListener(open);
            title.addMouseListener(open);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(event.isImportant() ? Theme.IMPORTANT_SOFT : Theme.EVENT_BLOCK);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(event.isImportant() ? Theme.IMPORTANT : Theme.ACCENT);
            g2.fillRoundRect(0, 0, 5, getHeight(), 4, 4);
            g2.dispose();
        }
    }
}
