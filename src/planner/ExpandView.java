package planner;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Large temporary view of one widget. A second widget of the same type (same data, same memo)
 * is shown over the board at twice the cells, on a dimmed layer; the widgets below are not
 * moved and the saved layout does not change. The copy works like the original (check boxes,
 * month buttons, memo text). Closed with its collapse button, a click on the dimmed area, or Esc.
 */
public class ExpandView {

    /** Each side gets this many times the cells (limited by the board size). */
    public static final int SCALE = 2;
    private static final Color DIM = new Color(0, 0, 0, 90);

    private final BoardPanel board;
    private Layer layer;
    private WidgetPanel copy;

    public ExpandView(BoardPanel board) {
        this.board = board;
    }

    public boolean isOpen() {
        return copy != null;
    }

    /** The large copy while open, otherwise null. */
    public WidgetPanel getCopy() {
        return copy;
    }

    /** Cells of the large view: SCALE times the size, moved inside the board when needed. */
    public static Rectangle expandedCells(Rectangle cells, int boardCols, int boardRows) {
        int cols = Math.min(cells.width * SCALE, boardCols);
        int rows = Math.min(cells.height * SCALE, boardRows);
        int col = Math.max(0, Math.min(cells.x, boardCols - cols));
        int row = Math.max(0, Math.min(cells.y, boardRows - rows));
        return new Rectangle(col, row, cols, rows);
    }

    public void open(WidgetPanel original) {
        if (board.isEditMode()) {
            return;
        }
        close(); // only one at a time
        JRootPane root = SwingUtilities.getRootPane(board);
        if (root == null) {
            return;
        }
        Rectangle cells = expandedCells(original.getGridRect(), board.getCols(), board.getRows());
        copy = board.newWidget(original.getType(), original.getRef());
        copy.setExpandAction(this::close, true);
        copy.setSolidText(board.getStore().isSolidText());
        JPopupMenu menu = Menus.popup();
        for (javax.swing.JMenuItem item : copy.createMenuItems()) {
            menu.add(item);
        }
        javax.swing.JMenuItem back = Menus.item("Back to normal size");
        back.addActionListener(e -> close());
        if (menu.getComponentCount() > 0) {
            menu.addSeparator();
        }
        menu.add(back);
        copy.setComponentPopupMenu(menu);

        layer = new Layer();
        JLayeredPane lp = root.getLayeredPane();
        Point at = SwingUtilities.convertPoint(board, 0, 0, lp);
        layer.setBounds(at.x, at.y, board.getWidth(), board.getHeight());
        layer.add(copy);
        copy.setGrid(cells); // same cell math as on the board (the layer has the board's origin)
        lp.add(layer, JLayeredPane.PALETTE_LAYER);
        lp.revalidate();
        lp.repaint();
    }

    public void close() {
        if (copy == null) {
            return;
        }
        WidgetPanel c = copy;
        Layer l = layer;
        copy = null;
        layer = null;
        c.dispose(); // stops listening to the store (and saves memo typing)
        java.awt.Container parent = l.getParent();
        if (parent != null) {
            parent.remove(l);
            parent.repaint();
        }
        board.repaint();
    }

    /** Dims the board and closes the view when the dimmed area is clicked. */
    private final class Layer extends JComponent {
        Layer() {
            setLayout(null);
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (copy != null && !copy.getBounds().contains(e.getPoint())) {
                        close();
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(DIM);
            g.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
