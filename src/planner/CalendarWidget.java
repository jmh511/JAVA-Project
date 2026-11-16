package planner;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/**
 * Monthly calendar. Dates that have events get a dot. Clicking a date opens that day.
 * At 3 rows or more, today's events are listed under the calendar.
 */
public class CalendarWidget extends WidgetPanel {

    private static final String[] WEEKDAYS = {"S", "M", "T", "W", "T", "F", "S"};

    private YearMonth shown = YearMonth.now();
    private final JPanel grid = new JPanel(new GridLayout(0, 7));
    private final JPanel agenda = new JPanel();
    private boolean wide;
    private boolean showAgenda;

    public CalendarWidget(DataStore store) {
        super(WidgetType.CALENDAR, store);
        JButton prev = Theme.flatButton("<");
        JButton next = Theme.flatButton(">");
        prev.setToolTipText("Previous month");
        next.setToolTipText("Next month");
        prev.addActionListener(e -> {
            shown = shown.minusMonths(1);
            refresh();
            finishBuild();
        });
        next.addActionListener(e -> {
            shown = shown.plusMonths(1);
            refresh();
            finishBuild();
        });
        addHeaderTool(prev);
        addHeaderTool(next);

        grid.setOpaque(false);
        agenda.setOpaque(false);
        agenda.setLayout(new BoxLayout(agenda, BoxLayout.Y_AXIS));
        agenda.setBorder(new EmptyBorder(6, 2, 0, 0));
        body.add(grid, BorderLayout.CENTER);
        body.add(agenda, BorderLayout.SOUTH);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        wide = cols >= 3;
        showAgenda = rows >= 3;
        refresh();
    }

    @Override
    protected void refresh() {
        titleLabel.setText(shown.format(wide ? Times.MONTH_LONG : Times.MONTH_SHORT));
        titleLabel.setToolTipText("Click the title to go back to this month");

        grid.removeAll();
        for (int i = 0; i < 7; i++) {
            JLabel l = Theme.label(WEEKDAYS[i], Theme.TINY, i == 0 ? Theme.SUNDAY : Theme.TEXT_MUTED);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            grid.add(l);
        }
        LocalDate first = shown.atDay(1);
        int blanks = first.getDayOfWeek().getValue() % 7; // Sunday -> 0
        for (int i = 0; i < blanks; i++) {
            grid.add(new JLabel());
        }
        for (int d = 1; d <= shown.lengthOfMonth(); d++) {
            grid.add(new DayCell(shown.atDay(d)));
        }
        int used = blanks + shown.lengthOfMonth();
        for (int i = used; i < 42; i++) { // always 6 week rows so the size does not jump
            grid.add(new JLabel());
        }

        agenda.removeAll();
        agenda.setVisible(showAgenda);
        if (showAgenda) {
            LocalDate today = LocalDate.now();
            List<Event> todays = store.eventsOn(today);
            agenda.add(Theme.label("Today " + today.format(Times.MD), Theme.SMALL, Theme.TEXT));
            if (todays.isEmpty()) {
                agenda.add(Theme.label("No events", Theme.TINY, Theme.TEXT_MUTED));
            }
            int max = Math.min(todays.size(), 2);
            for (int i = 0; i < max; i++) {
                Event e = todays.get(i);
                agenda.add(Theme.label(Times.hm(e.getStartMin()) + "  " + e.getTitle(), Theme.TINY,
                        e.isImportant() ? Theme.IMPORTANT : Theme.TEXT_MUTED));
            }
            if (todays.size() > max) {
                agenda.add(Theme.label("+" + (todays.size() - max) + " more", Theme.TINY, Theme.TEXT_MUTED));
            }
        }
    }

    @Override
    public List<JMenuItem> createMenuItems() {
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem thisMonth = new JMenuItem("Go to this month");
        thisMonth.addActionListener(e -> {
            shown = YearMonth.now();
            refresh();
            finishBuild();
        });
        items.add(thisMonth);
        return items;
    }

    /** One date in the month grid. */
    private class DayCell extends JComponent {
        private final LocalDate date;
        private final boolean hasEvents;
        private final boolean hasImportant;

        DayCell(LocalDate date) {
            this.date = date;
            List<Event> list = store.eventsOn(date);
            hasEvents = !list.isEmpty();
            hasImportant = list.stream().anyMatch(Event::isImportant);
            if (hasEvents) {
                StringBuilder tip = new StringBuilder();
                for (Event e : list) {
                    if (tip.length() > 0) {
                        tip.append(", ");
                    }
                    tip.append(e.getTitle());
                }
                setToolTipText(tip.toString());
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        DayDialog.show(CalendarWidget.this, store, date);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int size = Math.max(9, Math.min(12, h - 4));
            g2.setFont(new Font(Font.DIALOG, Font.PLAIN, size));
            FontMetrics fm = g2.getFontMetrics();
            String text = String.valueOf(date.getDayOfMonth());
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - 1;

            boolean today = date.equals(LocalDate.now());
            Color textColor = date.getDayOfWeek() == DayOfWeek.SUNDAY ? Theme.SUNDAY : Theme.TEXT;
            if (today) {
                // A ring with a blue number (not white on blue), so it stays clear when widgets are see-through.
                int d = Math.min(w, h) - 2;
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawOval((w - d) / 2, (h - d) / 2 - 1, d, d);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD));
                fm = g2.getFontMetrics();
                tx = (w - fm.stringWidth(text)) / 2;
                textColor = Theme.ACCENT;
            }
            g2.setColor(textColor);
            g2.drawString(text, tx, ty);

            if (hasEvents) {
                int dot = 4;
                g2.setColor(hasImportant ? Theme.IMPORTANT : Theme.ACCENT);
                g2.fillOval((w - dot) / 2, h - dot - 1, dot, dot);
            }
            g2.dispose();
        }
    }
}
