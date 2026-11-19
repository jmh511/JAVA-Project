package planner;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;

/**
 * Focus timer: a ring that empties as time goes by, the time left in the middle, and
 * Start / Pause (plus Reset and Skip when there is room). Study and break length are set with
 * "Set" (or the right-click menu). Study rings are blue, break rings green.
 */
public class FocusTimerWidget extends WidgetPanel {

    private static final Color BREAK_COLOR = new Color(0x34C759);

    private final FocusTimer timer;
    private final Runnable listener = this::onTimerChanged;
    private final Face face = new Face();
    private final JButton startButton = Theme.pillButton("Start");
    private final JButton resetButton = Theme.widgetButton("Reset");
    private final JButton skipButton = Theme.widgetButton("Skip");
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
    private boolean roomy;

    public FocusTimerWidget(DataStore store) {
        super(WidgetType.FOCUS, store);
        timer = FocusTimer.get(store);

        JButton set = Theme.widgetButton("Set");
        set.setToolTipText("Study and break length");
        set.addActionListener(e -> FocusSettingsDialog.show(this, store));
        addHeaderTool(set);

        startButton.addActionListener(e -> timer.toggle());
        resetButton.setToolTipText("Back to a new study session");
        resetButton.addActionListener(e -> timer.reset());
        skipButton.setToolTipText("End this part now");
        skipButton.addActionListener(e -> timer.skip());
        controls.setOpaque(false);
        controls.add(resetButton);
        controls.add(startButton);
        controls.add(skipButton);

        body.add(face, BorderLayout.CENTER);
        body.add(controls, BorderLayout.SOUTH);
        timer.addListener(listener);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        roomy = cols >= 3 || rows >= 3;
        refresh();
    }

    @Override
    protected void refresh() {
        resetButton.setVisible(roomy);
        skipButton.setVisible(roomy);
        onTimerChanged();
    }

    private void onTimerChanged() {
        String label = timer.isRunning() ? "Pause" : timer.isFresh() ? "Start" : "Resume";
        if (!label.equals(startButton.getText())) {
            startButton.setText(label);
            controls.revalidate();
        }
        face.repaint();
    }

    @Override
    public List<JMenuItem> createMenuItems() {
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem set = Menus.item("Set study and break time...");
        set.addActionListener(e -> FocusSettingsDialog.show(this, store));
        JMenuItem reset = Menus.item("Reset timer");
        reset.addActionListener(e -> timer.reset());
        items.add(set);
        items.add(reset);
        return items;
    }

    @Override
    public void dispose() {
        timer.removeListener(listener); // the timer itself keeps running
        super.dispose();
    }

    /** The ring, the time left, and the phase / today's count. */
    private final class Face extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int w = getWidth();
            int h = getHeight();
            int d = Math.min(w, h) - 10;
            if (d < 40) {
                g2.dispose();
                return;
            }
            int x = (w - d) / 2;
            int y = (h - d) / 2;
            float thick = Math.max(5f, d * 0.075f);
            boolean study = timer.getPhase() == FocusTimer.Phase.STUDY;
            Color ring = study ? Theme.wAccent : BREAK_COLOR;

            // track, then what is left (starts at the top, runs clockwise)
            g2.setStroke(new BasicStroke(thick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.wLine);
            g2.drawOval(x, y, d, d);
            double left = 1.0 - Math.max(0, Math.min(1, timer.progress()));
            if (left > 0.001) {
                g2.setColor(timer.isRunning() ? ring : Theme.withAlpha(ring, 150));
                g2.drawArc(x, y, d, d, 90, (int) Math.round(-360 * left));
            }

            // time left
            String time = FocusTimer.format(timer.remaining());
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, Math.max(14, Math.round(d * 0.21f))));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.wText);
            int ty = h / 2 + fm.getAscent() / 2 - fm.getDescent() / 2 - 2;
            g2.drawString(time, (w - fm.stringWidth(time)) / 2, ty);

            // phase above, today's sessions below
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, Math.max(10, Math.round(d * 0.085f))));
            fm = g2.getFontMetrics();
            String phase = timer.getPhase().getLabel().toUpperCase();
            g2.setColor(ring);
            g2.drawString(phase, (w - fm.stringWidth(phase)) / 2, ty - Math.round(d * 0.25f));
            if (roomy) {
                g2.setFont(new Font(Theme.FAMILY, Font.PLAIN, Math.max(10, Math.round(d * 0.075f))));
                fm = g2.getFontMetrics();
                int done = store.getFocusDoneToday();
                String today = done == 1 ? "1 session today" : done + " sessions today";
                g2.setColor(Theme.wMuted);
                g2.drawString(today, (w - fm.stringWidth(today)) / 2, ty + Math.round(d * 0.2f));
            }
            g2.dispose();
        }
    }
}
