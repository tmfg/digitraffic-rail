package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourneyTrack;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExDataset;

/**
 * Persists the resolved winning journey refs (and their planned tracks) for the real-time window from an
 * already-computed {@link NeTExDataset}, so real-time producers can point at journeys that actually exist in
 * the published ServiceJourney set. Consumes the {@link PublishedJourneyDraft}s the dataset already joined — it
 * does not re-fetch schedules, re-run the winning-schedule resolution or re-join ids to ServiceJourneys.
 *
 * <p>Not transactional itself: it is invoked inside {@link NeTExPackageService}'s persist transaction so the
 * package and the refs are stored (or rolled back) together.
 */
@Service
public class NeTExPublishedJourneyWriter {

    private static final Logger log = LoggerFactory.getLogger(NeTExPublishedJourneyWriter.class);

    private final NeTExPublishedJourneyRepository journeyRepo;

    private final boolean enabled;
    private final int lookbackDays;
    private final int lookaheadDays;

    public NeTExPublishedJourneyWriter(final NeTExPublishedJourneyRepository journeyRepo,
            @Value("${updater.netex.persist-journeys.enabled:true}") final boolean enabled,
            @Value("${updater.netex.persist-journeys.lookback-days:2}") final int lookbackDays,
            @Value("${updater.netex.persist-journeys.lookahead-days:2}") final int lookaheadDays) {
        this.journeyRepo = journeyRepo;
        this.enabled = enabled;
        this.lookbackDays = lookbackDays;
        this.lookaheadDays = lookaheadDays;
    }

    /**
     * Persists the published journey refs for the real-time window {@code [today-lookback, today+lookahead]}
     * under a new dataset version. A no-op when disabled. Old versions are pruned separately by
     * {@link NeTExPublishedJourneyCleanupService}.
     */
    public void persistWindow(final NeTExDataset dataset) {
        if (!enabled) {
            return;
        }
        final long startTime = System.currentTimeMillis();

        final LocalDate today = DateProvider.dateInHelsinki();
        // Always cover at least the SIRI operating-day window (its single source of truth), so widening it can
        // never leave SIRI reading a day this writer didn't persist.
        final int effectiveLookback = Math.max(lookbackDays, OperatingDayWindow.LOOKBACK_DAYS);
        final LocalDate windowStart = today.minusDays(effectiveLookback);
        final LocalDate windowEnd = today.plusDays(lookaheadDays);

        final long newVersion = Optional.ofNullable(journeyRepo.getMaxDatasetVersion()).orElse(0L) + 1;
        final ZonedDateTime now = DateProvider.nowInHelsinki();

        final List<NeTExPublishedJourney> journeys = new ArrayList<>();
        for (final PublishedJourneyDraft draft : dataset.publishedJourneys()) {
            final LocalDate departureDate = draft.trainId().departureDate;
            if (departureDate.isBefore(windowStart) || departureDate.isAfter(windowEnd)) {
                continue;
            }
            final NeTExPublishedJourney journey = new NeTExPublishedJourney(draft.trainId(), draft.serviceJourneyId(),
                    draft.lineRef(), draft.operatorRef(), draft.journeyPatternRef(), newVersion, now);
            for (final PublishedJourneyDraft.PublishedTrack track : draft.tracks()) {
                journey.addTrack(new NeTExPublishedJourneyTrack(track.stationShortCode(), track.plannedTrack(),
                        track.visitIndex()));
            }
            journeys.add(journey);
        }

        // Insert the new version (tracks cascade). Versions older than the retention period are dropped by the
        // separate cleanup job, so recent stale datasets stay queryable for debugging (SIRI only ever reads the
        // newest version).
        journeyRepo.persist(journeys);

        final int trackCount = journeys.stream().mapToInt(j -> j.tracks.size()).sum();

        final String outcome = journeys.isEmpty() ? "empty" : "success";
        final String line = StringUtil.format(
                "event=rail.netex.publish_journeys outcome={} dataset_version={} journeys={} tracks={} "
                        + "window_start={} window_end={} duration_ms={}",
                outcome, newVersion, journeys.size(), trackCount, windowStart, windowEnd,
                System.currentTimeMillis() - startTime);
        if (journeys.isEmpty()) {
            log.warn(line);
        } else {
            log.info(line);
        }
    }
}
