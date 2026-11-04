package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import static fi.livi.digitraffic.common.logging.LogFields.*;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExDataset;

/**
 * Persists the resolved winning journey refs (and their planned tracks) for the
 * real-time window from an already-computed {@link NeTExDataset}, so real-time
 * producers can point at journeys that actually exist in the published
 * ServiceJourney set.
 *
 * <p>
 * Used only by SIRI, which needs the journey ids to match its messages to
 * NeTEx. Does not affect the published NeTEx XML.
 *
 * <p>
 * Consumes the {@link PublishedJourneyDraft}s the dataset already joined. It
 * does not re-fetch schedules, re-run the winning-schedule resolution or
 * re-join ids to ServiceJourneys.
 *
 * <p>
 * Not transactional itself: it is invoked inside {@link NeTExPackageService}'s
 * persist transaction so the package and the refs are stored (or rolled back)
 * together.
 */
@Service
public class NeTExPublishedJourneyWriter {

    private static final Logger log = LoggerFactory.getLogger(NeTExPublishedJourneyWriter.class);

    private final NeTExPublishedJourneyRepository journeyRepo;
    private final NeTExPublishedJourneyBatchInserter batchInserter;

    private final boolean enabled;

    public NeTExPublishedJourneyWriter(final NeTExPublishedJourneyRepository journeyRepo,
            final NeTExPublishedJourneyBatchInserter batchInserter,
            @Value("${updater.netex.persist-journeys.enabled:true}") final boolean enabled) {
        this.journeyRepo = journeyRepo;
        this.batchInserter = batchInserter;
        this.enabled = enabled;
    }

    /**
     * Persists the dataset's published journey drafts under a new dataset version.
     * The drafts were already built for {@link NeTExDataset#publishedWindow()}, so
     * the writer does not compute a window of its own. A no-op when disabled. Old
     * versions are pruned separately by {@link NeTExPublishedJourneyCleanupService}.
     */
    public void persistWindow(final NeTExDataset dataset) {
        if (!enabled) {
            return;
        }
        final long startTime = System.currentTimeMillis();

        final PublishedJourneyWindow window = dataset.publishedWindow();

        final long newVersion = Optional.ofNullable(journeyRepo.getMaxDatasetVersion()).orElse(0L) + 1;
        final Instant now = Instant.now();

        final List<PublishedJourneyDraft> journeys = dataset.publishedJourneys();

        // Insert the new version. Versions older than the retention period are dropped by the separate cleanup
        // job, so recent stale datasets stay queryable for debugging (SIRI only ever reads the newest version).
        batchInserter.insert(journeys, newVersion, now);

        final int trackCount = journeys.stream().mapToInt(j -> j.tracks().size()).sum();

        final String outcome = journeys.isEmpty() ? "empty" : "success";
        final String line = StringUtil.format(
                "event=rail.netex.publish_journeys outcome={} dataset_version={} journeys={} trackCount={} "
                        + "window_start={} window_end={} duration={}",
                outcome, newVersion, journeys.size(), trackCount, window.start(), window.end(),
                durationSeconds(System.currentTimeMillis() - startTime));
        if (journeys.isEmpty()) {
            log.warn(line);
        } else {
            log.info(line);
        }
    }
}
