package planner;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * Common parent of all widgets. Holds the grid position, draws the card background,
 * and listens to the DataStore.
 */
public abstract class WidgetPanel extends JPanel implements DataStore.Listener {

    protected final DataStore store;
    private final WidgetType type;

    private int col;
    private int row;
    private int cols;
    private int rows;
    private boolean placed;

    protected final JLabel titleLabel;
    private final JPanel headerTools;
    /** Content area below the header. Subclasses put their content here. */
    protected final JPanel body;

    private boolean refreshPending;
    private LocalDate lastDate = LocalDate.now();

    protected WidgetPanel(WidgetType type, DataStore store) {
        super(new BorderLayout(0, 4));
        this.type = type;
        this.store = store;
        setOpaque(false);
        setBorder(new EmptyBorder(7, 10, 8, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        titleLabel = Theme.label(type.getTitle(), Theme.TITLE, Theme.TEXT);
        headerTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        headerTools.setOpaque(false);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(headerTools, BorderLayout.EAST);

        body = new JPanel(new BorderLayout());
        body.setOpaque(false);

        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        store.addListener(this);
    }

    protected void addHeaderTool(JComponent c) {
        headerTools.add(c);
    }

    /** 0..1, applied to the whole widget (card, text, controls) at once. */
    private float opacity = 1f;
    private BufferedImage buffer;

    /** True: text and lines stay fully visible; only light (background) pixels fade. */
    private boolean solidText = true;

    public void setOpacity(float value) {
        opacity = Math.max(0.05f, Math.min(1f, value));
        repaint();
    }

    public void setSolidText(boolean solid) {
        solidText = solid;
        repaint();
    }

    // Brightness range that separates "ink" (text, lines) from "background" (card, pastel blocks).
    // Brighter than INK_LIGHT: background, fades fully. Darker than INK_DARK: ink, stays solid.
    // In between (anti-aliased text edges): partly, so edges stay smooth.
    private static final int INK_LIGHT = 235;
    private static final int INK_DARK = 170;

    /**
     * Applies the opacity per pixel: background pixels get the widget opacity, dark pixels
     * (text, lines, borders) keep their own alpha. The buffer is TYPE_INT_ARGB (not premultiplied).
     */
    private void fadeBackgroundOnly(BufferedImage img) {
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        float range = INK_LIGHT - INK_DARK;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = p >>> 24;
            if (a == 0) {
                continue;
            }
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int brightness = (r * 299 + g * 587 + b * 114) / 1000;
            float ink = Math.max(0f, Math.min(1f, (INK_LIGHT - brightness) / range));
            int newA = Math.round(a * (opacity + (1f - opacity) * ink));
            px[i] = (newA << 24) | (p & 0x00FFFFFF);
        }
    }

    /** True when parts of this widget must not be repainted on their own (see WidgetRepaintManager). */
    public boolean isTranslucent() {
        return opacity < 0.999f;
    }

    /**
     * Fully opaque: normal painting. Otherwise the widget is painted into an image first and the
     * image is drawn with the chosen opacity, so overlapping layers do not blend unevenly.
     */
    @Override
    public void paint(Graphics g) {
        if (!isTranslucent()) {
            super.paint(g);
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        Graphics2D target = (Graphics2D) g;
        // Match the display scale (e.g. 200%) so text in the image stays sharp.
        double scale = Math.max(1.0, target.getTransform().getScaleX());
        int bw = (int) Math.ceil(w * scale);
        int bh = (int) Math.ceil(h * scale);
        if (buffer == null || buffer.getWidth() != bw || buffer.getHeight() != bh) {
            buffer = new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D bg = buffer.createGraphics();
        bg.setComposite(AlphaComposite.Clear);
        bg.fillRect(0, 0, bw, bh);
        bg.setComposite(AlphaComposite.SrcOver);
        bg.scale(scale, scale);
        super.paint(bg);
        bg.dispose();

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (solidText) {
            fadeBackgroundOnly(buffer); // opacity is now inside the image itself
        } else {
            g2.setComposite(AlphaComposite.SrcOver.derive(opacity));
        }
        g2.drawImage(buffer, 0, 0, w, h, null);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Theme.CARD_BG);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.setColor(Theme.CARD_BORDER);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        g2.dispose();
    }

    // ------------------------------------------------------------------ grid position

    public WidgetType getType() {
        return type;
    }

    /** Position and size in cells. */
    public Rectangle getGridRect() {
        return new Rectangle(col, row, cols, rows);
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    /** Places the widget on the board. Pixel bounds are always computed from the cells. */
    public void setGrid(int col, int row, int cols, int rows) {
        boolean sizeChanged = !placed || cols != this.cols || rows != this.rows;
        this.col = col;
        this.row = row;
        this.cols = cols;
        this.rows = rows;
        placed = true;
        setBounds(col * Theme.cellW() + Theme.GAP, row * Theme.cellH() + Theme.GAP,
                cols * Theme.cellW() - 2 * Theme.GAP, rows * Theme.cellH() - 2 * Theme.GAP);
        if (sizeChanged) {
            onGridSizeChanged(cols, rows);
            finishBuild();
        }
        revalidate();
        repaint();
    }

    public void setGrid(Rectangle cells) {
        setGrid(cells.x, cells.y, cells.width, cells.height);
    }

    // ------------------------------------------------------------------ hooks for subclasses

    /** Called when the number of cells changes. Choose how much information to show here. */
    protected abstract void onGridSizeChanged(int cols, int rows);

    /** Re-reads data from the store and rebuilds the content. */
    protected abstract void refresh();

    /** Extra items for this widget's right-click menu. */
    public List<JMenuItem> createMenuItems() {
        return Collections.emptyList();
    }

    /** Called by the board about every 30 seconds. Rebuilds when the date has changed (midnight). */
    public void minuteTick() {
        LocalDate now = LocalDate.now();
        if (!now.equals(lastDate)) {
            lastDate = now;
            refresh();
            finishBuild();
        }
    }

    @Override
    public final void dataChanged() {
        // Coalesce several changes into one rebuild and never rebuild inside another component's event.
        if (refreshPending) {
            return;
        }
        refreshPending = true;
        SwingUtilities.invokeLater(() -> {
            refreshPending = false;
            refresh();
            finishBuild();
        });
    }

    /** Called when the widget is removed from the board. The data itself stays in the store. */
    public void dispose() {
        store.removeListener(this);
    }

    /** Lets every child show the widget's right-click menu, then lays out again. */
    protected void finishBuild() {
        inheritPopup(this);
        revalidate();
        repaint();
    }

    private static void inheritPopup(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JComponent) {
                ((JComponent) child).setInheritsPopupMenu(true);
            }
            if (child instanceof Container) {
                inheritPopup((Container) child);
            }
        }
    }

    protected Window owner() {
        return SwingUtilities.getWindowAncestor(this);
    }
}
