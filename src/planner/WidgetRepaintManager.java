package planner;

import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/**
 * A see-through widget is drawn as one image with an opacity (WidgetPanel.paint).
 * If Swing repainted only a part (for example a scroll bar or a checkbox) on its own,
 * that part would be drawn fully opaque. So any repaint inside a see-through widget is
 * turned into a repaint of the widget itself.
 */
public class WidgetRepaintManager extends RepaintManager {

    @Override
    public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
        WidgetPanel widget = c instanceof WidgetPanel ? null
                : (WidgetPanel) SwingUtilities.getAncestorOfClass(WidgetPanel.class, c);
        if (widget != null && widget.isTranslucent()) {
            Rectangle r = SwingUtilities.convertRectangle(c, new Rectangle(x, y, w, h), widget);
            super.addDirtyRegion(widget, r.x, r.y, r.width, r.height);
            return;
        }
        super.addDirtyRegion(c, x, y, w, h);
    }
}
