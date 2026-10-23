package fi.livi.rata.avoindata.updater.service.gtfs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.RealtimeFeedFileNames;

class RealtimeFeedCleanupServiceTest {
    private static final int BATCH_SIZE = 100;
    private static final int SIRI_ET_BATCH_SIZE = 5;
    private static final int MAX_BATCHES_PER_RUN = 3;

    private static final String LOCATIONS = RealtimeFeedFileNames.GTFS_RT_LOCATIONS;

    private GeneratedExportRepository repository;
    private RealtimeFeedCleanupService service;

    @BeforeEach
    void setUp() {
        repository = mock(GeneratedExportRepository.class);
        service = new RealtimeFeedCleanupService(repository, 60, BATCH_SIZE, SIRI_ET_BATCH_SIZE,
                MAX_BATCHES_PER_RUN);
    }

    private void givenIds(final String fileName, final long newestId, final long newestExpiredId) {
        when(repository.findLatestIdByFileName(fileName)).thenReturn(Optional.of(newestId));
        when(repository.findLatestIdByFileNameCreatedBefore(eq(fileName), any(ZonedDateTime.class)))
                .thenReturn(Optional.of(newestExpiredId));
    }

    @Test
    void givenExpiredSnapshots_whenCleaning_thenDeletesUpToNewestExpiredUntilABatchIsNotFull() {
        givenIds(LOCATIONS, 100, 90);
        when(repository.deleteBatchByFileNameUpToId(LOCATIONS, 90, BATCH_SIZE)).thenReturn(BATCH_SIZE, 7);

        service.deleteOldSnapshots();

        verify(repository, times(2)).deleteBatchByFileNameUpToId(LOCATIONS, 90, BATCH_SIZE);
    }

    @Test
    void givenFeedHasStoppedUpdating_whenCleaning_thenNewestSnapshotIsKept() {
        // every snapshot of the feed is older than the retention, including the one the API serves
        givenIds(LOCATIONS, 100, 100);

        service.deleteOldSnapshots();

        verify(repository).deleteBatchByFileNameUpToId(LOCATIONS, 99, BATCH_SIZE);
    }

    @Test
    void givenNoExpiredSnapshots_whenCleaning_thenNothingIsDeleted() {
        when(repository.findLatestIdByFileName(anyString())).thenReturn(Optional.of(100L));

        service.deleteOldSnapshots();

        verify(repository, never()).deleteBatchByFileNameUpToId(anyString(), anyLong(), anyInt());
    }

    @Test
    void givenLargeBacklog_whenCleaning_thenStopsAfterMaxBatches() {
        givenIds(LOCATIONS, 100_000, 90_000);
        when(repository.deleteBatchByFileNameUpToId(LOCATIONS, 90_000, BATCH_SIZE)).thenReturn(BATCH_SIZE);

        service.deleteOldSnapshots();

        verify(repository, times(MAX_BATCHES_PER_RUN)).deleteBatchByFileNameUpToId(LOCATIONS, 90_000, BATCH_SIZE);
    }

    @Test
    void givenSiriEt_whenCleaning_thenUsesSmallerBatches() {
        givenIds(RealtimeFeedFileNames.SIRI_ET, 100, 90);

        service.deleteOldSnapshots();

        verify(repository).deleteBatchByFileNameUpToId(RealtimeFeedFileNames.SIRI_ET, 90, SIRI_ET_BATCH_SIZE);
    }

    @Test
    void givenOneFeedFails_whenCleaning_thenOtherFeedsAreStillCleaned() {
        when(repository.findLatestIdByFileName(LOCATIONS)).thenThrow(new RuntimeException("database unavailable"));
        givenIds(RealtimeFeedFileNames.SIRI_VM, 100, 90);

        service.deleteOldSnapshots();

        verify(repository).deleteBatchByFileNameUpToId(RealtimeFeedFileNames.SIRI_VM, 90, BATCH_SIZE);
    }
}
