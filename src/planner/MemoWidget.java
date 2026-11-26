package planner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Sticky note. Unlike the other widgets it shows its own text (one Memo in the DataStore),
 * so several memos can be on the board. Typing is saved about one second after it stops,
 * when the memo loses focus, and when the program exits.
 *
 * On the desktop the board window never takes keyboard focus (so it does not jump above other
 * windows); there a click on the memo opens a small editor window instead.
 */
public class MemoWidget extends WidgetPanel {

    /** Save delay after the last key press. */
    private static final int SAVE_DELAY_MS = 1000;

    /** Every memo view that is alive, so unsaved typing can be written at exit. */
    private static final List<MemoWidget> LIVE = new ArrayList<>();

    private final long memoId;
    private final JTextArea area = new JTextArea();
    private final JScrollPane scroll;
    private final Timer saveTimer;
    /** True while the text is set from the store (not typed). */
    private boolean updating;

    public MemoWidget(DataStore store, long memoId) {
        super(WidgetType.MEMO, store);
        this.memoId = memoId;
        titleLabel.setForeground(Theme.MEMO_MUTED);
        titleLabel.setFont(Theme.SMALL);

        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(Theme.BODY.deriveFont(13f));
        area.setForeground(Theme.MEMO_TEXT);
        area.setCaretColor(Theme.MEMO_TEXT);
        area.setSelectionColor(new Color(0, 0, 0, 40));
        area.setBorder(new EmptyBorder(0, 0, 0, 0));
        Memo m = store.getMemo(memoId);
        area.setText(m == null ? "" : m.getText());
        area.setCaretPosition(0);

        scroll = new JScrollPane(area);
        scroll.setBorder(null);
        Theme.styleScroll(scroll);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        body.add(scroll, BorderLayout.CENTER);

        saveTimer = new Timer(SAVE_DELAY_MS, e -> push());
        saveTimer.setRepeats(false);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute changes only
            }
        });
        area.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                push();
            }
        });
        // On the desktop the board cannot get keys: edit in a small window instead.
        area.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                boolean viaDialog = needsEditorWindow();
                area.setEditable(!viaDialog);
                if (viaDialog && SwingUtilities.isLeftMouseButton(e)) {
                    e.consume();
                    SwingUtilities.invokeLater(() -> openEditor());
                }
            }
        });
        LIVE.add(this);
    }

    @Override
    public long getRef() {
        return memoId;
    }

    public String getText() {
        return area.getText();
    }

    private void typed() {
        if (!updating) {
            saveTimer.restart();
        }
    }

    /** Writes the typed text to the store now (if it changed). */
    public void push() {
        saveTimer.stop();
        Memo m = store.getMemo(memoId);
        if (m != null && !m.getText().equals(area.getText())) {
            store.setMemoText(m, area.getText());
        }
    }

    /** Saves unsaved typing of every memo view (called at exit). */
    public static void flushAll() {
        for (MemoWidget w : new ArrayList<>(LIVE)) {
            w.push();
        }
    }

    /** True when this memo is on the desktop board, which never gets keyboard focus. */
    private boolean needsEditorWindow() {
        Window w = SwingUtilities.getWindowAncestor(this);
        return w instanceof BoardFrame && ((BoardFrame) w).isEmbedded();
    }

    private void openEditor() {
        push();
        MemoEditDialog.show(this, store, memoId);
    }

    @Override
    protected void onGridSizeChanged(int cols, int rows) {
        boolean tiny = cols == 1 && rows == 1;
        // 1 x 1: no title and no scroll bar, so the text gets all the room.
        titleLabel.setText(tiny ? " " : "Memo");
        scroll.setVerticalScrollBarPolicy(tiny ? JScrollPane.VERTICAL_SCROLLBAR_NEVER
                : JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        setBorder(tiny ? new EmptyBorder(3, 8, 6, 4) : new EmptyBorder(7, 10, 8, 8));
    }

    /** Another view of the same memo (expanded copy, pop-out window, editor) saved new text. */
    @Override
    protected void refresh() {
        Memo m = store.getMemo(memoId);
        if (m == null) {
            return;
        }
        if (!area.isFocusOwner() && !saveTimer.isRunning() && !m.getText().equals(area.getText())) {
            updating = true;
            int caret = Math.min(area.getCaretPosition(), m.getText().length());
            area.setText(m.getText());
            area.setCaretPosition(caret);
            updating = false;
        }
        repaint();
    }

    @Override
    public List<JMenuItem> createMenuItems() {
        List<JMenuItem> items = new ArrayList<>();
        JMenuItem edit = Menus.item("Edit memo...");
        edit.addActionListener(e -> openEditor());
        items.add(edit);
        Memo m = store.getMemo(memoId);
        for (String name : Theme.MEMO_COLOR_NAMES) {
            boolean current = m != null && m.getColor().equals(name);
            JMenuItem c = Menus.colorItem("Color: " + Theme.memoColorLabel(name), Theme.memoColor(name), current);
            c.addActionListener(e -> {
                Memo now = store.getMemo(memoId);
                if (now != null) {
                    store.setMemoColor(now, name);
                }
            });
            items.add(c);
        }
        return items;
    }

    /** The menu shows the current color, so it is built again when the color changes. */
    public String getColorName() {
        Memo m = store.getMemo(memoId);
        return m == null ? Theme.MEMO_COLOR_NAMES[0] : m.getColor();
    }

    @Override
    protected boolean brightTextOnDark() {
        return false; // dark text on light paper in every style
    }

    /** Paper in the memo's color instead of the normal card. */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - 1;
        int h = getHeight() - 1;
        int arc = Theme.CARD_ARC;
        for (int i = 3; i >= 1; i--) {
            g2.setColor(new Color(0, 0, 0, 8 - i));
            g2.fillRoundRect(-i + 1, -i + 2, w + 2 * i - 2, h + 2 * i - 2, arc + 2 * i, arc + 2 * i);
        }
        g2.setColor(Theme.memoColor(getColorName()));
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setColor(new Color(0, 0, 0, 28));
        g2.drawRoundRect(0, 0, w, h, arc, arc);
        g2.dispose();
    }

    @Override
    public void dispose() {
        push();
        LIVE.remove(this);
        super.dispose();
    }
}
