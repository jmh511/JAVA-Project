package planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

/**
 * Settings window. Every change is applied (and saved) immediately.
 *  - Desktop grid: match the board to this PC's desktop icon grid. Done only when the user
 *    presses the button (not on every start), e.g. after moving to another computer.
 *  - Widgets: opacity, and whether text stays 100% visible.
 */
public class SettingsDialog extends JDialog {

    private final BoardFrame frame;
    private final BoardPanel board;
    private final DataStore store;
    private final JLabel gridStatus = new JLabel(" "); // not empty, so it gets a real height

    public static void show(BoardFrame frame, DataStore store) {
        SettingsDialog d = new SettingsDialog(frame, store);
        d.fit();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
    }

    private SettingsDialog(BoardFrame frame, DataStore store) {
        super(frame, "Settings", ModalityType.APPLICATION_MODAL);
        this.frame = frame;
        this.board = frame.getBoard();
        this.store = store;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(14, 16, 8, 16));
        content.add(styleSection());
        content.add(Box.createVerticalStrut(12));
        content.add(gridSection());
        content.add(Box.createVerticalStrut(12));
        content.add(widgetSection());

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        JPanel root = new JPanel(new BorderLayout());
        root.add(content, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(close);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        updateGridStatus();
    }

    private JPanel gridSection() {
        JPanel p = section("Board");
        JButton match = new JButton("Match this PC's desktop grid");
        JLabel matchHelp = Theme.label("<html>Cells become the same size as the desktop icon grid, and the board<br>"
                + "covers the whole desktop. Press again after moving to another PC<br>"
                + "or changing the display scale.</html>", Theme.SMALL, Theme.TEXT_MUTED);
        JButton fitFree = new JButton("Fit board between desktop icons");
        JLabel fitHelp = Theme.label("<html>Normal 80 x 80 cells; the largest board that covers no icons,<br>"
                + "moved to that free space.</html>", Theme.SMALL, Theme.TEXT_MUTED);
        JLabel resizeHelp = Theme.label("<html>To change the size by hand: Edit layout, then drag the board's edge.</html>",
                Theme.SMALL, Theme.TEXT_MUTED);

        for (JButton b : new JButton[]{match, fitFree}) {
            b.setEnabled(frame.isEmbedded());
            if (!frame.isEmbedded()) {
                b.setToolTipText("Needs desktop mode (Windows, JDK 22 or newer)");
            }
        }
        match.addActionListener(e -> {
            String problem = frame.matchDesktopGrid();
            updateGridStatus();
            fit();
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "Desktop grid", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        fitFree.addActionListener(e -> {
            frame.fitBoardToFreeSpace(true);
            updateGridStatus();
            fit();
        });

        add(p, gridStatus);
        add(p, Box.createVerticalStrut(8));
        add(p, match);
        add(p, Box.createVerticalStrut(3));
        add(p, matchHelp);
        add(p, Box.createVerticalStrut(10));
        add(p, fitFree);
        add(p, Box.createVerticalStrut(3));
        add(p, fitHelp);
        add(p, Box.createVerticalStrut(10));
        add(p, resizeHelp);
        return p;
    }

    private JPanel styleSection() {
        JPanel p = section("Style");
        JPanel row = new JPanel(new java.awt.GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        List<StyleTile> tiles = new ArrayList<>();
        for (Theme.Style s : Theme.Style.values()) {
            StyleTile tile = new StyleTile(s);
            tile.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    board.setWidgetStyle(s);
                    for (StyleTile t : tiles) {
                        t.repaint();
                    }
                }
            });
            tiles.add(tile);
            row.add(tile);
        }
        add(p, row);
        return p;
    }

    /** Small preview of a widget card in one style, on a colored "wallpaper". Click to choose it. */
    private static final class StyleTile extends JComponent {
        private final Theme.Style style;

        StyleTile(Theme.Style style) {
            this.style = style;
            setPreferredSize(new java.awt.Dimension(118, 104));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setToolTipText(style.getLabel());
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int boxH = getHeight() - 20;
            boolean selected = Theme.style() == style;

            // Wallpaper behind the card, so the glass look is visible.
            g2.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x5E9C8F), w, boxH, new java.awt.Color(0x2F5F6B)));
            g2.fillRoundRect(0, 0, w - 1, boxH, 14, 14);

            // Read this style's palette, paint the mini card, then restore the current style.
            Theme.Style current = Theme.style();
            Theme.setStyle(style);
            int cx = 12;
            int cy = 10;
            int cw = w - 24;
            int ch = boxH - 20;
            if (style == Theme.Style.LIGHT) {
                g2.setColor(new java.awt.Color(0, 0, 0, 30));
                g2.fillRoundRect(cx, cy + 2, cw, ch, 16, 16);
            }
            g2.setColor(Theme.wCard);
            g2.fillRoundRect(cx, cy, cw, ch, 16, 16);
            if (style == Theme.Style.GLASS) {
                g2.setPaint(new java.awt.GradientPaint(0, cy, new java.awt.Color(255, 255, 255, 70), 0, cy + ch / 2f,
                        new java.awt.Color(255, 255, 255, 0)));
                g2.fillRoundRect(cx, cy, cw, ch, 16, 16);
            }
            g2.setColor(Theme.wEdge);
            g2.drawRoundRect(cx, cy, cw, ch, 16, 16);
            g2.setFont(Theme.TITLE.deriveFont(10f));
            g2.setColor(Theme.wText);
            g2.drawString("Nov 2026", cx + 8, cy + 16);
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 7; c++) {
                    int x = cx + 9 + c * ((cw - 18) / 7);
                    int y = cy + 26 + r * 12;
                    boolean today = r == 1 && c == 3;
                    g2.setColor(today ? Theme.ACCENT : Theme.wMuted);
                    g2.fillOval(x, y, today ? 6 : 4, today ? 6 : 4);
                }
            }
            Theme.setStyle(current);

            if (selected) {
                g2.setColor(Theme.ACCENT);
                g2.setStroke(new java.awt.BasicStroke(2.5f));
                g2.drawRoundRect(1, 1, w - 3, boxH - 2, 14, 14);
            }
            g2.setFont(Theme.SMALL.deriveFont(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
            g2.setColor(selected ? Theme.ACCENT : Theme.TEXT);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            g2.drawString(style.getLabel(), (w - fm.stringWidth(style.getLabel())) / 2, getHeight() - 5);
            g2.dispose();
        }
    }

    private JPanel widgetSection() {
        JPanel p = section("Widgets");
        JLabel value = new JLabel();
        JSlider slider = new JSlider(30, 100, store.getWidgetOpacity());
        slider.setMajorTickSpacing(10);
        slider.setSnapToTicks(true);
        slider.setPaintTicks(true);
        value.setText("Opacity: " + store.getWidgetOpacity() + "%");
        slider.addChangeListener(e -> {
            value.setText("Opacity: " + slider.getValue() + "%");
            if (!slider.getValueIsAdjusting()) {
                board.setWidgetOpacity(slider.getValue());
            }
        });
        JCheckBox sharp = new JCheckBox("Keep text 100% visible (sharp)", store.isSolidText());
        sharp.addActionListener(e -> board.setSolidText(sharp.isSelected()));
        add(p, value);
        add(p, slider);
        add(p, sharp);
        return p;
    }

    /** pack() plus some spare width: measured text can be a little narrower than drawn text. */
    private void fit() {
        pack();
        setSize(getWidth() + 40, getHeight());
    }

    private void updateGridStatus() {
        String cell = Theme.cellW() + " x " + Theme.cellH() + " px";
        String size = board.getCols() + " x " + board.getRows() + " cells";
        gridStatus.setText(store.isGridMatched()
                ? "Matched to this desktop:  cell " + cell + ",  board " + size
                : "Not matched:  cell " + cell + ",  board " + size);
    }

    private static JPanel section(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(title), new EmptyBorder(4, 8, 8, 8)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); // stretch to the window
        return p;
    }

    private static void add(JPanel p, Component c) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (jc instanceof JLabel || jc instanceof JCheckBox || jc instanceof JSlider) {
                // Let text use the full width instead of its (sometimes slightly short) measured width.
                jc.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
            }
        }
        p.add(c);
    }
}
