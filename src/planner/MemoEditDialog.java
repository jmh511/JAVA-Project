package planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * Editor window for a memo. Used where the memo itself cannot get keyboard input
 * (the board on the desktop), and from the memo's "Edit memo..." menu item.
 * Enter makes a new line; Ctrl+Enter or Save closes and saves, Esc cancels.
 */
public class MemoEditDialog extends JDialog {

    private final DataStore store;
    private final long memoId;
    private final JTextArea area = new JTextArea(10, 28);

    public static void show(Component parent, DataStore store, long memoId) {
        Memo m = store.getMemo(memoId);
        if (m == null) {
            return;
        }
        new MemoEditDialog(SwingUtilities.getWindowAncestor(parent), store, m).open(parent);
    }

    private MemoEditDialog(Window owner, DataStore store, Memo memo) {
        super(owner, "Memo", ModalityType.APPLICATION_MODAL);
        this.store = store;
        this.memoId = memo.getId();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        area.setText(memo.getText());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.BODY.deriveFont(14f));
        area.setForeground(Theme.MEMO_TEXT);
        area.setCaretColor(Theme.MEMO_TEXT);
        area.setBackground(Theme.memoColor(memo.getColor()));
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(new LineBorder(Theme.CARD_BORDER));
        scroll.setPreferredSize(new Dimension(340, 240));

        JButton save = Theme.primaryButton("Save");
        JButton cancel = new JButton("Cancel");
        cancel.setFont(Theme.BODY);
        save.addActionListener(e -> saveAndClose());
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.CARD_BG);
        root.setBorder(new EmptyBorder(14, 14, 12, 14));
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);

        JComponent rp = getRootPane();
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        rp.getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
        rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "save");
        rp.getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                saveAndClose();
            }
        });
    }

    private void saveAndClose() {
        Memo m = store.getMemo(memoId);
        if (m != null) {
            store.setMemoText(m, area.getText());
        }
        dispose();
    }

    private void open(Component near) {
        pack();
        setLocationRelativeTo(near);
        area.setCaretPosition(area.getDocument().getLength());
        SwingUtilities.invokeLater(area::requestFocusInWindow);
        setVisible(true);
    }
}
