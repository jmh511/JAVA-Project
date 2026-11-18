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

    // ------------------------------------------------------------------ fixed colors
    // Used by dialogs and the board itself; the same in every style.

    public static final Color BOARD_BG = new Color(0xF3F3EF);
    public static final Color GRID_LINE = new Color(0xE3E3DC);
    public static final Color GRID_LINE_EDIT = new Color(0xC9CFDA);
    public static final Color CARD_BG = Color.WHITE;
    public static final Color CARD_BORDER = new Color(0xDEDEE3);
    public static final Color ACCENT = new Color(0x007AFF);
    public static final Color ACCENT_SOFT = new Color(0xE5F0FF);
    public static final Color TEXT = new Color(0x1C1C1E);
    public static final Color TEXT_MUTED = new Color(0x8E8E93);
    public static final Color IMPORTANT = new Color(0xFF3B30);
    public static final Color IMPORTANT_SOFT = new Color(0xFFE5E3);
    public static final Color EVENT_BLOCK = new Color(0xE5F0FF);
    public static final Color DANGER = new Color(0xE5372B);
    public static final Color SUNDAY = new Color(0xFF453A);
    public static final Color BANNER_BG = new Color(0xFFF4D6);

    // ------------------------------------------------------------------ widget styles

    /** Look of the widget cards, chosen in Settings. */
    public enum Style {
        LIGHT("Light"), DARK("Dark"), GLASS("Liquid Glass");

        private final String label;

        Style(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private static Style style = Style.LIGHT;

    // Widget palette: changes with the style (see setStyle). Widgets read these when they are built
    // or painted, so after a style change the board rebuilds its widgets.
    public static Color wCard;
    public static Color wEdge;
    public static Color wText;
    public static Color wMuted;
    public static Color wLine;
    public static Color wBlock;
    public static Color wImportantSoft;
    public static Color wAccentSoft;
    /** Accent for buttons and highlights inside widgets (brighter on glass so it stays readable). */
    public static Color wAccent;

    /** Corner radius of widget cards (rounder than before, like macOS widgets). */
    public static final int CARD_ARC = 24;

    static {
        setStyle(Style.LIGHT);
    }

    public static Style style() {
        return style;
    }

    public static void setStyle(Style s) {
        style = s;
        switch (s) {
            case DARK:
                wCard = new Color(0x1F1F22);
                wEdge = new Color(255, 255, 255, 26);
                wText = new Color(0xF2F2F7);
                wMuted = new Color(0x98989F);
                wLine = new Color(0x37373B);
                wBlock = new Color(0x1C3554);
                wImportantSoft = new Color(0x4A2524);
                wAccentSoft = new Color(0x1C3554);
                wAccent = new Color(0x0A84FF);
                break;
            case GLASS:
                // Tinted see-through glass with a bright rim; white text (like the reference).
                wCard = new Color(26, 38, 40, 128);
                wEdge = new Color(255, 255, 255, 110);
                wText = Color.WHITE;
                wMuted = new Color(235, 242, 242, 175);
                wLine = new Color(255, 255, 255, 42);
                wBlock = new Color(255, 255, 255, 46);
                wImportantSoft = new Color(255, 105, 95, 80);
                wAccentSoft = new Color(255, 255, 255, 40);
                wAccent = new Color(0x9BDCFF);
                break;
            default:
                wCard = Color.WHITE;
                wEdge = new Color(0, 0, 0, 20);
                wText = TEXT;
                wMuted = TEXT_MUTED;
                wLine = new Color(0xEBEBF0);
                wBlock = EVENT_BLOCK;
                wImportantSoft = IMPORTANT_SOFT;
                wAccentSoft = ACCENT_SOFT;
                wAccent = ACCENT;
        }
    }

    /** True when widget text is lighter than the card (Dark and Liquid Glass). */
    public static boolean lightInk() {
        return style != Style.LIGHT;
    }

    // ------------------------------------------------------------------ fonts

    /**
     * Malgun Gothic covers both Korean and Latin text (a font without Korean glyphs would show
     * boxes for Korean titles). Falls back to the logical Dialog font if it is not installed.
     */
    public static final String FAMILY = pickFamily();

    private static String pickFamily() {
        try {
            for (String f : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (f.equals("Malgun Gothic")) {
                    return f;
                }
            }
        } catch (RuntimeException e) {
            // fall through
        }
        return Font.DIALOG;
    }

    public static final Font TITLE = new Font(FAMILY, Font.BOLD, 13);
    public static final Font BODY = new Font(FAMILY, Font.PLAIN, 12);
    public static final Font SMALL = new Font(FAMILY, Font.PLAIN, 11);
    public static final Font TINY = new Font(FAMILY, Font.PLAIN, 10);

    // ------------------------------------------------------------------ checkbox and scroll bar

    /** Round macOS-like check mark: empty ring, or a filled blue circle with a white tick. */
    private static final class CheckIcon implements javax.swing.Icon {
        private final boolean checked;

        CheckIcon(boolean checked) {
            this.checked = checked;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = getIconWidth() - 2;
            if (checked) {
                g2.setColor(ACCENT);
                g2.fillOval(x + 1, y + 1, d, d);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{x + 4, x + 7, x + 12}, new int[]{y + 8, y + 11, y + 5}, 3);
            } else {
                g2.setColor(wMuted);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(x + 1, y + 1, d, d);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }

    /** Gives a checkbox the round check mark and the widget text colors. */
    public static void styleCheck(javax.swing.JCheckBox cb, boolean done) {
        cb.setIcon(new CheckIcon(false));
        cb.setSelectedIcon(new CheckIcon(true));
        cb.setIconTextGap(6);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        cb.setForeground(done ? wMuted : wText);
    }

    /** Thin rounded scroll thumb, no track and no arrow buttons. */
    private static final class ThinScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void paintTrack(java.awt.Graphics g, javax.swing.JComponent c, java.awt.Rectangle r) {
            // no track
        }

        @Override
        protected void paintThumb(java.awt.Graphics g, javax.swing.JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(withAlpha(wMuted, 120));
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, r.width - 4, r.width - 4);
            g2.dispose();
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new java.awt.Dimension(0, 0));
            b.setMinimumSize(new java.awt.Dimension(0, 0));
            b.setMaximumSize(new java.awt.Dimension(0, 0));
            return b;
        }
    }

    /** Transparent scroll pane with a thin scroll bar, for use inside widgets. */
    public static void styleScroll(javax.swing.JScrollPane sp) {
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        javax.swing.JScrollBar bar = sp.getVerticalScrollBar();
        bar.setUI(new ThinScrollBarUI());
        bar.setOpaque(false);
        bar.setPreferredSize(new java.awt.Dimension(8, 0));
    }

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

    /** Borderless button inside a widget, in the widget style's accent color. */
    public static JButton widgetButton(String text) {
        JButton b = flatButton(text);
        b.setForeground(wAccent);
        return b;
    }

    /** Filled blue button for the main action of a window (one per window). */
    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(BODY.deriveFont(Font.BOLD));
        b.setForeground(Color.WHITE);
        b.setBackground(ACCENT);
        // Paint our own flat background (the Windows look and feel ignores setBackground).
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(new javax.swing.border.EmptyBorder(6, 16, 6, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Text field that shows a grey example text while it is empty. */
    public static javax.swing.JTextField hintField(String hint, int columns) {
        javax.swing.JTextField f = new javax.swing.JTextField(columns) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont());
                    java.awt.Insets in = getInsets();
                    int y = in.top + (getHeight() - in.top - in.bottom - g2.getFontMetrics().getHeight()) / 2
                            + g2.getFontMetrics().getAscent();
                    g2.drawString(hint, in.left + 2, y);
                    g2.dispose();
                }
            }
        };
        f.setFont(BODY);
        return f;
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
