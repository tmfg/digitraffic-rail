package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

/**
 * The departure dates whose published journey refs are stored for SIRI,
 * {@code [start, end]} inclusive.
 * <p>
 * The NeTEx package spans more than a year while only these days are stored,
 * so both the draft build and the writer use this window: building drafts for
 * the whole package would allocate a journey per train per day and throw away
 * almost all of them.
 */
public record PublishedJourneyWindow(LocalDate start, LocalDate end) {

    /**
     * The lookback is never narrower than the SIRI operating-day window, so SIRI never reads a day nobody wrote.
     */
    public static PublishedJourneyWindow of(final LocalDate today, final int lookbackDays, final int lookaheadDays) {
        final int effectiveLookback = Math.max(lookbackDays, OperatingDayWindow.LOOKBACK_DAYS);
        return new PublishedJourneyWindow(today.minusDays(effectiveLookback), today.plusDays(lookaheadDays));
    }

    public boolean contains(final LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
