package planner;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

import javax.swing.JComponent;

/**
 * System status, updated every second:
 *  - small sizes: two ring gauges (CPU and memory);
 *  - 3 x 3 cells or more: a CPU graph of the last minute, a memory ring and a disk bar.
 */
public class SystemWidget extends WidgetPanel {

    private static final Color MEMORY_COLOR = new Color(0xAF52DE);
    private static final Color DISK_COLOR = new Color(0xFF9F0A);

    private final SystemSampler sampler = SystemSampler.get();
    private final Runnable listener = this::onSample;
    private final Gauges gauges = new Gauges();
    private boolean detailed;

    public SystemWidget(DataStore store) {
        super(WidgetType.SYSTEM, store);
        body.add(gauges, BorderLayout.CENTER);
        sampler.addListener(listener);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        detailed = cols >= 3 && rows >= 3;
        refresh();
    }

    @Override
    protected void refresh() {
        onSample();
    }

    private void onSample() {
        int cpu = (int) Math.round(sampler.cpuNow() * 100);
        titleLabel.setText(detailed ? "System" : "System  " + cpu + "%");
        gauges.repaint();
    }

    @Override
    public void dispose() {
        sampler.removeListener(listener);
        super.dispose();
    }

    private Color cpuColor() {
        return Theme.wAccent;
    }

    private final class Gauges extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            if (detailed) {
                paintDetailed(g2);
            } else {
                paintCompact(g2);
            }
            g2.dispose();
        }

        /** Two rings side by side. */
        private void paintCompact(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int d = Math.min(w / 2 - 10, h - 22);
            if (d < 30) {
                return;
            }
            int y = (h - 18 - d) / 2;
            ring(g2, w / 4 - d / 2, y, d, sampler.cpuNow(), cpuColor(), "CPU");
            ring(g2, w * 3 / 4 - d / 2, y, d, sampler.memoryNow(), MEMORY_COLOR, "Memory");
        }

        /** CPU graph on top; memory ring and disk bar below. */
        private void paintDetailed(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int graphH = Math.max(40, h * 46 / 100);

            // --- CPU: label, current value, area graph of the last minute
            g2.setFont(Theme.SMALL.deriveFont(Font.BOLD));
            g2.setColor(Theme.wMuted);
            g2.drawString("CPU", 0, 12);
            String now = Math.round(sampler.cpuNow() * 100) + "%";
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, 18));
            g2.setColor(Theme.wText);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(now, w - fm.stringWidth(now) - 2, 16);
            int gy = 22;
            int gh = graphH - gy;
            areaGraph(g2, 0, gy, w - 2, gh, sampler.cpuHistory(), cpuColor());

            // --- bottom: memory ring (left) and disk bar (right)
            int top = graphH + 10;
            int bottomH = h - top;
            int d = Math.min(bottomH - 18, w * 40 / 100);
            if (d >= 30) {
                ring(g2, 4, top + (bottomH - 18 - d) / 2, d, sampler.memoryNow(), MEMORY_COLOR, "Memory");
            }
            int bx = Math.max(d + 18, w * 46 / 100);
            int bw = w - bx - 4;
            if (bw > 40) {
                diskBar(g2, bx, top, bw, bottomH);
            }
        }

        /** Ring gauge with the percentage inside and a caption below. */
        private void ring(Graphics2D g2, int x, int y, int d, double value, Color color, String caption) {
            float thick = Math.max(4f, d * 0.11f);
            int inset = Math.round(thick / 2);
            int dd = d - 2 * inset;
            g2.setStroke(new BasicStroke(thick, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Theme.wLine);
            g2.drawOval(x + inset, y + inset, dd, dd);
            double v = Math.max(0, Math.min(1, value));
            if (v > 0.005) {
                g2.setColor(color);
                g2.drawArc(x + inset, y + inset, dd, dd, 90, (int) Math.round(-360 * v));
            }
            String pct = Math.round(v * 100) + "%";
            g2.setFont(new Font(Theme.FAMILY, Font.BOLD, Math.max(10, Math.round(d * 0.24f))));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.wText);
            g2.drawString(pct, x + (d - fm.stringWidth(pct)) / 2, y + d / 2 + fm.getAscent() / 2 - 2);
            g2.setFont(Theme.SMALL);
            fm = g2.getFontMetrics();
            g2.setColor(Theme.wMuted);
            g2.drawString(caption, x + (d - fm.stringWidth(caption)) / 2, y + d + 14);
        }

        /** Filled line graph, values 0..1, oldest on the left; faint lines at 50% and 100%. */
        private void areaGraph(Graphics2D g2, int x, int y, int w, int h, double[] values, Color color) {
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Theme.wLine);
            g2.drawLine(x, y, x + w, y);
            g2.drawLine(x, y + h / 2, x + w, y + h / 2);
            g2.drawLine(x, y + h, x + w, y + h);
            if (values.length < 2) {
                return;
            }
            double step = w / (double) (SystemSampler.HISTORY - 1);
            double startX = x + w - (values.length - 1) * step; // newest value at the right edge
            Path2D.Double line = new Path2D.Double();
            Path2D.Double area = new Path2D.Double();
            for (int i = 0; i < values.length; i++) {
                double px = startX + i * step;
                double py = y + h - values[i] * h;
                if (i == 0) {
                    line.moveTo(px, py);
                    area.moveTo(px, y + h);
                    area.lineTo(px, py);
                } else {
                    line.lineTo(px, py);
                    area.lineTo(px, py);
                }
            }
            area.lineTo(x + w, y + h);
            area.closePath();
            g2.setPaint(new GradientPaint(0, y, Theme.withAlpha(color, 110), 0, y + h, Theme.withAlpha(color, 10)));
            g2.fill(area);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(line);
        }

        /** Disk: name, a used / total bar, and free space. */
        private void diskBar(Graphics2D g2, int x, int y, int w, int h) {
            long total = sampler.diskTotal();
            long free = sampler.diskFree();
            double used = total > 0 ? (total - free) / (double) total : 0;
            int cy = y + h / 2 - 18;
            g2.setFont(Theme.SMALL.deriveFont(Font.BOLD));
            g2.setColor(Theme.wMuted);
            g2.drawString("Disk " + sampler.diskName(), x, cy);
            int barY = cy + 8;
            int barH = 10;
            g2.setColor(Theme.wLine);
            g2.fillRoundRect(x, barY, w, barH, barH, barH);
            g2.setColor(used > 0.9 ? Theme.IMPORTANT : DISK_COLOR);
            g2.fillRoundRect(x, barY, Math.max(barH, (int) Math.round(w * used)), barH, barH, barH);
            g2.setFont(Theme.SMALL);
            g2.setColor(Theme.wText);
            g2.drawString(SystemSampler.gb(free) + " free", x, barY + barH + 16);
            g2.setColor(Theme.wMuted);
            g2.drawString("of " + SystemSampler.gb(total), x, barY + barH + 31);
            String mem = SystemSampler.gb(sampler.memoryUsed()) + " / " + SystemSampler.gb(sampler.memoryTotal());
            g2.setFont(Theme.TINY);
            g2.drawString("RAM " + mem, x, barY + barH + 47);
        }
    }
}
