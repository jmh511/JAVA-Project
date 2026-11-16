package planner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JLabel;

/**
 * Shared style values (grid size, colors, fonts) and small UI helpers.
 */
public final class Theme {

    private Theme() {
    }

    /** Default size of one grid cell in pixels (square). */
    public static final int CELL = 80;

    /** Current cell size. Can be matched to the desktop icon grid (e.g. 75 x 102). */
    private static int cellW = CELL;
    private static int cellH = CELL;

    public static int cellW() {
        return cellW;
    }

    public static int cellH() {
        return cellH;
    }

    public static void setCellSize(int w, int h) {
        cellW = Math.max(40, w);
        cellH = Math.max(40, h);
    }
    /** Inner gap between a widget and its cell border. */
    public static final int GAP = 4;
    /** Default board size in cells (the user can change it from the menu). */
    public static final int COLS = 12;
    public static final int ROWS = 6;

    public static final Color BOARD_BG = new Color(0xF3F3EF);
    public static final Color GRID_LINE = new Color(0xE3E3DC);
    public static final Color GRID_LINE_EDIT = new Color(0xC9CFDA);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color CARD_BORDER = new Color(0xDADAD2);
    public static final Color ACCENT = new Color(0x2F7BD9);
    public static final Color ACCENT_SOFT = new Color(0xE3EEFB);
    public static final Color TEXT = new Color(0x222222);
    public static final Color TEXT_MUTED = new Color(0x8A8A85);
    public static final Color IMPORTANT = new Color(0xE0533D);
    public static final Color IMPORTANT_SOFT = new Color(0xFBE3DE);
    public static final Color EVENT_BLOCK = new Color(0xD6E6FA);
    public static final Color DANGER = new Color(0xD64545);
    public static final Color SUNDAY = new Color(0xD0574A);
    public static final Color BANNER_BG = new Color(0xFFF4D6);

    // Logical "Dialog" font so that Korean user input is still rendered correctly.
    public static final Font TITLE = new Font(Font.DIALOG, Font.BOLD, 13);
    public static final Font BODY = new Font(Font.DIALOG, Font.PLAIN, 12);
    public static final Font SMALL = new Font(Font.DIALOG, Font.PLAIN, 11);
    public static final Font TINY = new Font(Font.DIALOG, Font.PLAIN, 10);

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Borderless text button used inside widgets. */
    public static JButton flatButton(String text) {
        JButton b = new JButton(text);
        b.setFont(SMALL);
        b.setForeground(ACCENT);
        b.setMargin(new Insets(0, 4, 0, 4));
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    /** Small generated icon (2x2 grid of tiles) for the window and tray. */
    public static BufferedImage appIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int pad = Math.max(1, size / 16);
        int tile = (size - pad * 3) / 2;
        int arc = Math.max(2, size / 5);
        g.setColor(ACCENT);
        g.fillRoundRect(pad, pad, tile, tile, arc, arc);
        g.fillRoundRect(pad * 2 + tile, pad, tile, tile, arc, arc);
        g.fillRoundRect(pad, pad * 2 + tile, tile * 2 + pad, tile, arc, arc);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1f, size / 16f)));
        g.drawLine(pad * 2 + tile / 2, pad * 2 + tile + tile / 2, pad + tile * 2 - pad, pad * 2 + tile + tile / 2);
        g.dispose();
        return img;
    }
}
