package fi.livi.rata.avoindata.updater.service.netex;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import static fi.livi.digitraffic.common.logging.LogFields.*;

/**
 * Prunes published journey refs older than the retention period.
 *
 * <p>The FK cascade on {@code netex_published_journey_track} removes the tracks of the pruned journeys.
 */
@Service
public class NeTExPublishedJourneyCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NeTExPublishedJourneyCleanupService.class);

    private final NeTExPublishedJourneyRepository journeyRepo;
    private final int retentionDays;

    public NeTExPublishedJourneyCleanupService(final NeTExPublishedJourneyRepository journeyRepo,
            @Value("${updater.netex.persist-journeys.retention-days:7}") final int retentionDays) {
        this.journeyRepo = journeyRepo;
        this.retentionDays = retentionDays;
    }

    /**
     * Deletes every journey generated before {@code now - retentionDays}. SIRI only ever reads the newest
     * dataset version, so recent stale versions are kept solely for debugging.
     */
    @Transactional
    public void deleteOldJourneys() {
        final long startTime = System.currentTimeMillis();
        final Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        final int prunedRows = journeyRepo.deleteByGeneratedAtBefore(cutoff);

        log.info(StringUtil.format(
                "event=rail.netex.prune_journeys outcome=success cutoff={} pruned_rows={} retention_days={} "
                        + "duration={}",
                cutoff, prunedRows, retentionDays,
                durationSeconds(System.currentTimeMillis() - startTime)));
    }
}
