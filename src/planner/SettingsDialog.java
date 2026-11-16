package planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;

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
        JPanel p = section("Desktop grid");
        JLabel help = Theme.label("<html>Cells become the same size as the desktop icon grid, and the board<br>"
                + "covers the whole desktop. Press again after moving to another PC<br>"
                + "or changing the display scale.</html>", Theme.SMALL, Theme.TEXT_MUTED);
        JButton match = new JButton("Match this PC's desktop grid");
        match.setEnabled(frame.isEmbedded());
        if (!frame.isEmbedded()) {
            match.setToolTipText("Needs desktop mode (Windows, JDK 22 or newer)");
        }
        match.addActionListener(e -> {
            String problem = frame.matchDesktopGrid();
            updateGridStatus();
            fit();
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "Desktop grid", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        add(p, gridStatus);
        add(p, Box.createVerticalStrut(6));
        add(p, match);
        add(p, Box.createVerticalStrut(6));
        add(p, help);
        return p;
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
