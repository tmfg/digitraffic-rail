package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;
import java.util.List;

/**
 * The real-time "operating day" window shared by the NeTEx persist path and the SIRI read path: the dates whose
 * trains may still be running now. Finnish rail journeys cross at most one midnight, so it is yesterday + today.
 *
 * <p>Single source of truth for the coupling between the two sides: SIRI reads the published journey refs for
 * exactly this window, and NeTEx persistence must cover at least it (otherwise SIRI's hard dependency on the
 * published refs could not be satisfied). Widening {@link #LOOKBACK_DAYS} widens both automatically, so the two
 * windows can never silently drift apart.
 */
public final class OperatingDayWindow {

    /** Days before today the operating day reaches back (1 ⇒ yesterday + today). */
    public static final int LOOKBACK_DAYS = 1;

    private OperatingDayWindow() {
    }

    /** The earliest operating date for the given {@code today}. */
    public static LocalDate startDate(final LocalDate today) {
        return today.minusDays(LOOKBACK_DAYS);
    }

    /** The inclusive operating dates {@code [today - LOOKBACK_DAYS, today]}. */
    public static List<LocalDate> dates(final LocalDate today) {
        return startDate(today).datesUntil(today.plusDays(1)).toList();
    }
}
