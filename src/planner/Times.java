package planner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Date and time helpers. Times of day are stored as "minutes from 00:00" (0..1440).
 */
public final class Times {

    private Times() {
    }

    public static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M/d", Locale.ENGLISH);
    public static final DateTimeFormatter DOW = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    public static final DateTimeFormatter DOW_MD = DateTimeFormatter.ofPattern("EEE M/d", Locale.ENGLISH);
    public static final DateTimeFormatter MONTH_LONG = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    public static final int DAY_MINUTES = 24 * 60;

    /** 545 -> "09:05", 1440 -> "24:00". */
    public static String hm(int minutes) {
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    public static int parseHm(String s) {
        String[] p = s.trim().split(":");
        int value = Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        if (value < 0 || value > DAY_MINUTES) {
            throw new IllegalArgumentException("Time out of range: " + s);
        }
        return value;
    }

    /** List of times from fromMin to toMin (inclusive) in 30 minute steps. */
    public static String[] halfHours(int fromMin, int toMin) {
        List<String> list = new ArrayList<>();
        for (int m = fromMin; m <= toMin; m += 30) {
            list.add(hm(m));
        }
        return list.toArray(new String[0]);
    }

    public static int nowMinutes() {
        LocalTime t = LocalTime.now();
        return t.getHour() * 60 + t.getMinute();
    }

    /** Next half-hour slot after now, limited so that a 30 minute event still fits in the day. */
    public static int nextHalfHour() {
        int m = (nowMinutes() / 30 + 1) * 30;
        return Math.min(m, DAY_MINUTES - 30);
    }

    /** Sunday of the week that contains the given date. */
    public static LocalDate weekStart(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    }
}
