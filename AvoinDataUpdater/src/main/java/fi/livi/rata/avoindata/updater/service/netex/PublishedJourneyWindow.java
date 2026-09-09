package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * The departure dates whose published journey refs are persisted for real-time use. The NeTEx feed spans
 * years while only these few days are ever stored, so the window is owned here rather than at the write
 * point: whoever builds the drafts has to narrow to it too, or the run allocates a journey per train per
 * day across the whole feed horizon and throws away all but a handful.
 */
@Component
public class PublishedJourneyWindow {

    private final boolean enabled;
    private final int lookbackDays;
    private final int lookaheadDays;

    public PublishedJourneyWindow(
            @Value("${updater.netex.persist-journeys.enabled:true}") final boolean enabled,
            @Value("${updater.netex.persist-journeys.lookback-days:2}") final int lookbackDays,
            @Value("${updater.netex.persist-journeys.lookahead-days:2}") final int lookaheadDays) {
        this.enabled = enabled;
        // Widening is safe, narrowing past the SIRI operating-day window would leave it reading a day nobody wrote.
        this.lookbackDays = Math.max(lookbackDays, OperatingDayWindow.LOOKBACK_DAYS);
        this.lookaheadDays = lookaheadDays;
    }

    public boolean enabled() {
        return enabled;
    }

    public LocalDate start() {
        return DateProvider.dateInHelsinki().minusDays(lookbackDays);
    }

    public LocalDate end() {
        return DateProvider.dateInHelsinki().plusDays(lookaheadDays);
    }
}
