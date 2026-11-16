package planner;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;

/**
 * Date input (yyyy-MM-dd) with up/down arrows, built only from standard Swing.
 */
public class DateField extends JSpinner {

    public DateField(LocalDate initial) {
        super(new SpinnerDateModel());
        setEditor(new JSpinner.DateEditor(this, "yyyy-MM-dd"));
        setDate(initial);
    }

    public LocalDate getDate() {
        Date d = (Date) getValue();
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public void setDate(LocalDate date) {
        setValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
    }
}
