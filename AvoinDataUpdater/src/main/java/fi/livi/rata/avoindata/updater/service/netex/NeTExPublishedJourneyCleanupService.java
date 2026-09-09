package fi.livi.rata.avoindata.updater.service.netex;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.utils.DateProvider;

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
            @Value("${updater.netex.persist-journeys.retention-days:14}") final int retentionDays) {
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
        final ZonedDateTime cutoff = DateProvider.nowInHelsinki().minusDays(retentionDays);

        final int prunedRows = journeyRepo.deleteByGeneratedAtBefore(cutoff);

        log.info(StringUtil.format(
                "event=rail.netex.prune_journeys outcome=success cutoff={} pruned_rows={} retention_days={} "
                        + "duration_ms={}",
                cutoff, prunedRows, retentionDays, System.currentTimeMillis() - startTime));
    }
}
