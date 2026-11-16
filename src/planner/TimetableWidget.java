package planner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/**
 * Today's events drawn as blocks on a vertical time axis.
 * Hour labels on the hour, a faint line every 30 minutes, and a red "now" line.
 */
public class TimetableWidget extends WidgetPanel {

    private static final int HOUR_HEIGHT = 44;
    private static final int LABEL_WIDTH = 40;
    private static final int TOP = 8;

    private final Timeline timeline = new Timeline();
    private final JScrollPane scroll = new JScrollPane(timeline);
    private boolean showTimes;
    private boolean scrolledToNow;

    public TimetableWidget(DataStore store) {
        super(WidgetType.TIMETABLE, store);
        JButton add = Theme.flatButton("+ Add");
        add.setToolTipText("Add an event for today");
        add.addActionListener(e -> EventDialog.showAdd(this, store, LocalDate.now(), Times.nextHalfHour()));
        addHeaderTool(add);

        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(HOUR_HEIGHT / 2);
        body.add(scroll);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        showTimes = cols >= 3;
        refresh();
    }

    @Override
    protected void refresh() {
        titleLabel.setText("Today  " + LocalDate.now().format(Times.DOW_MD));
        timeline.revalidate();
        timeline.repaint();
        if (!scrolledToNow) {
            scrolledToNow = true;
            SwingUtilities.invokeLater(this::scrollToNow);
        }
    }

    @Override
    public void minuteTick() {
        super.minuteTick();
        timeline.repaint(); // move the "now" line
    }

    @Override
    public List<JMenuItem> createMenuItems() {
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem add = new JMenuItem("Add event...");
        add.addActionListener(e -> EventDialog.showAdd(this, store, LocalDate.now(), Times.nextHalfHour()));
        JMenuItem settings = new JMenuItem("Settings...");
        settings.addActionListener(e -> TimetableSettingsDialog.show(this, store));
        JMenuItem now = new JMenuItem("Scroll to now");
        now.addActionListener(e -> scrollToNow());
        items.add(add);
        items.add(settings);
        items.add(now);
        return items;
    }

    private void scrollToNow() {
        int y = timeline.yOf(Times.nowMinutes()) - HOUR_HEIGHT;
        JViewport vp = scroll.getViewport();
        int max = Math.max(0, timeline.getPreferredSize().height - vp.getExtentSize().height);
        vp.setViewPosition(new Point(0, Math.max(0, Math.min(y, max))));
    }

    /** The drawing surface inside the scroll pane. */
    private class Timeline extends JComponent implements Scrollable {
        private final List<Rectangle> blockRects = new ArrayList<>();
        private final List<Event> blockEvents = new ArrayList<>();

        Timeline() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    Event hit = eventAt(e.getPoint());
                    if (hit != null) {
                        EventDialog.showEdit(TimetableWidget.this, store, hit);
                    } else if (e.getClickCount() == 2) {
                        EventDialog.showAdd(TimetableWidget.this, store, LocalDate.now(), minutesAt(e.getY()));
                    }
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    Event hit = eventAt(e.getPoint());
                    setCursor(Cursor.getPredefinedCursor(hit != null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    setToolTipText(hit != null ? hit.timeRange() + "  " + hit.getTitle()
                            : "Double-click to add an event");
                }
            });
        }

        private int startMin() {
            return store.getTimetableStartHour() * 60;
        }

        private int endMin() {
            return store.getTimetableEndHour() * 60;
        }

        int yOf(int minutes) {
            return TOP + (minutes - startMin()) * HOUR_HEIGHT / 60;
        }

        int minutesAt(int y) {
            int m = startMin() + (y - TOP) * 60 / HOUR_HEIGHT;
            return Math.max(startMin(), Math.min(m, endMin() - 30));
        }

        Event eventAt(Point p) {
            for (int i = blockRects.size() - 1; i >= 0; i--) {
                if (blockRects.get(i).contains(p)) {
                    return blockEvents.get(i);
                }
            }
            return null;
        }

        @Override
        public Dimension getPreferredSize() {
            int hours = store.getTimetableEndHour() - store.getTimetableStartHour();
            return new Dimension(100, TOP * 2 + hours * HOUR_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int start = startMin();
            int end = endMin();

            // Grid: labelled line on the hour, faint line on the half hour.
            g2.setFont(Theme.TINY);
            FontMetrics fm = g2.getFontMetrics();
            for (int m = start; m <= end; m += 30) {
                int y = yOf(m);
                if (m % 60 == 0) {
                    g2.setColor(Theme.GRID_LINE);
                    g2.drawLine(LABEL_WIDTH, y, w, y);
                    g2.setColor(Theme.TEXT_MUTED);
                    g2.drawString(Times.hm(m), 2, y + fm.getAscent() / 2 - 1);
                } else {
                    g2.setColor(new Color(0xF0F0EC));
                    g2.drawLine(LABEL_WIDTH, y, w, y);
                }
            }

            // Event blocks. Overlapping events are placed side by side.
            blockRects.clear();
            blockEvents.clear();
            List<Event> events = store.eventsOn(LocalDate.now());
            int[] column = new int[events.size()];
            int[] columnCount = new int[events.size()];
            assignColumns(events, column, columnCount);
            int areaX = LABEL_WIDTH + 2;
            int areaW = w - areaX - 4;
            for (int i = 0; i < events.size(); i++) {
                Event e = events.get(i);
                int s = Math.max(e.getStartMin(), start);
                int en = Math.min(e.getEndMin(), end);
                if (en <= s) {
                    continue; // outside the visible range
                }
                int colW = areaW / columnCount[i];
                Rectangle r = new Rectangle(areaX + column[i] * colW, yOf(s) + 1, colW - 2, yOf(en) - yOf(s) - 2);
                blockRects.add(r);
                blockEvents.add(e);
                drawBlock(g2, e, r);
            }

            // Now line.
            int now = Times.nowMinutes();
            if (now >= start && now <= end) {
                int y = yOf(now);
                g2.setColor(Theme.IMPORTANT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawLine(LABEL_WIDTH, y, w, y);
                g2.fillOval(LABEL_WIDTH - 3, y - 3, 7, 7);
            }
            g2.dispose();
        }

        private void drawBlock(Graphics2D g2, Event e, Rectangle r) {
            Color fill = e.isImportant() ? Theme.IMPORTANT_SOFT : Theme.EVENT_BLOCK;
            Color bar = e.isImportant() ? Theme.IMPORTANT : Theme.ACCENT;
            g2.setColor(fill);
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g2.setColor(bar);
            g2.fillRoundRect(r.x, r.y, 4, r.height, 4, 4);

            Graphics2D t = (Graphics2D) g2.create();
            t.clipRect(r.x + 6, r.y, r.width - 8, r.height);
            t.setColor(Theme.TEXT);
            t.setFont(Theme.SMALL);
            FontMetrics fm = t.getFontMetrics();
            int ty = r.y + fm.getAscent() + 2;
            if (r.height < fm.getHeight()) {
                ty = r.y + r.height - 2;
            }
            t.drawString(e.getTitle(), r.x + 8, ty);
            if (showTimes && r.height >= fm.getHeight() * 2 + 2) {
                t.setFont(Theme.TINY);
                t.setColor(Theme.TEXT_MUTED);
                t.drawString(e.timeRange(), r.x + 8, ty + fm.getHeight());
            }
            t.dispose();
        }

        /** Greedy column assignment for groups of overlapping events (events are sorted by start). */
        private void assignColumns(List<Event> events, int[] column, int[] columnCount) {
            int n = events.size();
            int i = 0;
            while (i < n) {
                // A group is a run of events that overlap each other (directly or through others).
                List<Integer> columnEnds = new ArrayList<>();
                int groupEnd = events.get(i).getEndMin();
                int j = i;
                while (j < n && (j == i || events.get(j).getStartMin() < groupEnd)) {
                    Event e = events.get(j);
                    int c = 0;
                    while (c < columnEnds.size() && columnEnds.get(c) > e.getStartMin()) {
                        c++;
                    }
                    if (c == columnEnds.size()) {
                        columnEnds.add(e.getEndMin());
                    } else {
                        columnEnds.set(c, e.getEndMin());
                    }
                    column[j] = c;
                    groupEnd = Math.max(groupEnd, e.getEndMin());
                    j++;
                }
                for (int k = i; k < j; k++) {
                    columnCount[k] = columnEnds.size();
                }
                i = j;
            }
        }

        // Scrollable: follow the viewport width, scroll vertically.
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return HOUR_HEIGHT / 2;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height - HOUR_HEIGHT;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
