package planner;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;

/**
 * Base class for the small modal input dialogs: a label/field form, an error line,
 * and a button row. Enter = OK, Esc = Cancel.
 */
public abstract class FormDialog extends JDialog {

    private final JPanel form = new JPanel(new GridBagLayout());
    private final JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel errorLabel = Theme.label(" ", Theme.SMALL, Theme.DANGER);
    private int formRow;

    protected FormDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JButton ok = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> onSave());
        cancel.addActionListener(e -> dispose());

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        rightButtons.add(cancel);
        rightButtons.add(ok);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(leftButtons, BorderLayout.WEST);
        buttons.add(rightButtons, BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(errorLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(14, 16, 12, 16));
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);

        getRootPane().setDefaultButton(ok);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                dispose();
            }
        });
    }

    protected void addRow(String label, Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = formRow++;
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        if (label != null) {
            c.gridx = 0;
            form.add(new JLabel(label), c);
        }
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);
        form.add(field, c);
    }

    protected void addLeftButton(JButton b) {
        leftButtons.add(b);
    }

    protected void showError(String message) {
        errorLabel.setText(message);
    }

    protected void open() {
        pack();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    /** Validate input, store it, and dispose the dialog (or call showError). */
    protected abstract void onSave();
}
