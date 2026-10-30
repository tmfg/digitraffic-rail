package fi.livi.rata.avoindata.updater.service.gtfs;

import static fi.livi.digitraffic.common.logging.LogFields.durationSeconds;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.digitraffic.common.util.StringUtil;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.RealtimeFeedFileNames;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Deletes realtime feed snapshots older than the retention period. The API only
 * serves the newest snapshot of each
 * feed, so the newest one is always kept, even when the feed has stopped
 * updating.
 */
@Service
public class RealtimeFeedCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RealtimeFeedCleanupService.class);

    private final GeneratedExportRepository repository;
    private final Duration retention;
    private final int batchSize;
    private final int siriEtBatchSize;

    public RealtimeFeedCleanupService(final GeneratedExportRepository repository,
            @Value("${updater.realtime-feed.cleanup.retention-minutes:60}") final int retentionMinutes,
            @Value("${updater.realtime-feed.cleanup.batch-size:1000}") final int batchSize,
            @Value("${updater.realtime-feed.cleanup.siri-et-batch-size:50}") final int siriEtBatchSize) {
        this.repository = repository;
        this.retention = Duration.ofMinutes(retentionMinutes);
        this.batchSize = batchSize;
        this.siriEtBatchSize = siriEtBatchSize;
    }

    @Scheduled(fixedDelayString = "${updater.realtime-feed.cleanup.fixed-delay-ms:300000}", initialDelay = 1000 * 60
            * 2)
    public void deleteOldSnapshots() {
        final ZonedDateTime cutoff = DateProvider.nowInHelsinki().minus(retention);

        for (final String fileName : RealtimeFeedFileNames.ALL) {
            try {
                deleteOldSnapshots(fileName, cutoff);
            } catch (final Exception e) {
                log.error(StringUtil.format(
                        "event=rail.realtime_feed.prune outcome=error file_name={} error.type={}",
                        fileName, e.getClass().getSimpleName()), e);
            }
        }
    }

    private void deleteOldSnapshots(final String fileName, final ZonedDateTime cutoff) {
        final long startTime = System.currentTimeMillis();
        final Optional<Long> newestId = repository.findLatestIdByFileName(fileName);
        final Optional<Long> newestExpiredId = repository.findLatestIdByFileNameCreatedBefore(fileName, cutoff);
        if (newestId.isEmpty() || newestExpiredId.isEmpty()) {
            return;
        }

        // if feed is not updating, don't delete latest even if expired
        final long maxId = Math.min(newestExpiredId.get(), newestId.get() - 1);
        final int maxRows = RealtimeFeedFileNames.SIRI_ET.equals(fileName) ? siriEtBatchSize : batchSize;
        final int prunedRows = repository.deleteBatchByFileNameUpToId(fileName, maxId, maxRows);

        log.info(StringUtil.format(
                "event=rail.realtime_feed.prune outcome=success file_name={} cutoff={} pruned_rows={} batch_size={} "
                        + "duration={}",
                fileName, cutoff, prunedRows, maxRows,
                durationSeconds(System.currentTimeMillis() - startTime)));
    }
}
