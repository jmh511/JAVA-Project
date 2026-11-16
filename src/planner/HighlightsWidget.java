package planner;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;

/**
 * Only the events marked as important, for this week or this month.
 */
public class HighlightsWidget extends WidgetPanel {

    private final JToggleButton weekButton = new SlackToggle();
    private final JToggleButton monthButton = new SlackToggle();
    private final DefaultListModel<Event> model = new DefaultListModel<>();
    private final JList<Event> list = new JList<>(model);
    private final JScrollPane scroll = new JScrollPane(list);
    private final JLabel emptyLabel = Theme.label("No important events", Theme.SMALL, Theme.TEXT_MUTED);
    private boolean compact;

    public HighlightsWidget(DataStore store) {
        super(WidgetType.HIGHLIGHTS, store);

        ButtonGroup group = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[]{weekButton, monthButton}) {
            b.setFont(Theme.SMALL);
            b.setFocusPainted(false);
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setBorder(new EmptyBorder(0, 4, 0, 4));
            b.addItemListener(e -> {
                styleToggles();
                refresh();
                finishBuild();
            });
            group.add(b);
            addHeaderTool(b);
        }
        weekButton.setSelected(true);

        list.setOpaque(false);
        list.setFocusable(false);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2 && i >= 0
                        && list.getCellBounds(i, i).contains(e.getPoint())) {
                    EventDialog.showEdit(HighlightsWidget.this, store, model.get(i));
                }
            }
        });
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        body.add(scroll);
        styleToggles();
    }

    private void styleToggles() {
        for (JToggleButton b : new JToggleButton[]{weekButton, monthButton}) {
            // Same (bold) font for both, so the width never changes and the text is not cut off.
            b.setForeground(b.isSelected() ? Theme.ACCENT : Theme.TEXT_MUTED);
            b.setFont(Theme.SMALL.deriveFont(Font.BOLD));
        }
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        compact = cols < 3;
        weekButton.setText(compact ? "W" : "Week");
        monthButton.setText(compact ? "M" : "Month");
        weekButton.setToolTipText("This week");
        monthButton.setToolTipText("This month");
        refresh();
    }

    @Override
    protected void refresh() {
        LocalDate today = LocalDate.now();
        LocalDate from;
        LocalDate to;
        if (monthButton.isSelected()) {
            YearMonth ym = YearMonth.from(today);
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        } else {
            from = Times.weekStart(today);
            to = from.plusDays(6);
        }
        List<Event> items = store.importantBetween(from, to);
        model.clear();
        for (Event e : items) {
            model.addElement(e);
        }
        body.removeAll();
        if (items.isEmpty()) {
            body.add(emptyLabel, java.awt.BorderLayout.NORTH);
        } else {
            body.add(scroll);
        }
    }

    /** Toggle with a few spare pixels, because measured text can be slightly narrower than drawn text. */
    private static class SlackToggle extends JToggleButton {
        @Override
        public java.awt.Dimension getPreferredSize() {
            java.awt.Dimension d = super.getPreferredSize();
            return new java.awt.Dimension(d.width + 6, d.height);
        }
    }

    private class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                      boolean selected, boolean focus) {
            Event e = (Event) value;
            String date = e.getDate().format(compact ? Times.MD : Times.DOW_MD);
            String text = compact ? date + "  " + e.getTitle()
                    : date + "  " + Times.hm(e.getStartMin()) + "  " + e.getTitle();
            JLabel c = (JLabel) super.getListCellRendererComponent(l, text, index, false, false);
            c.setOpaque(false);
            c.setFont(Theme.SMALL);
            c.setBorder(new EmptyBorder(1, 0, 1, 0));
            boolean past = e.getDate().isBefore(LocalDate.now());
            c.setForeground(past ? Theme.TEXT_MUTED : Theme.TEXT);
            c.setToolTipText(e.getDate().format(Times.DOW_MD) + " " + e.timeRange() + "  " + e.getTitle());
            return c;
        }
    }
}
