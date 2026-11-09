package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExDataset;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExGenerationResult;
import fi.livi.rata.avoindata.updater.service.netex.PublishedJourneyDraft.PublishedTrack;

/**
 * The package, its journeys and their tracks are written in one transaction. The journeys and tracks go in with
 * JDBC, so this checks through the real package transaction that a track insert failing after the journeys were
 * written leaves nothing behind. It is not {@code @Transactional} itself, so the commit or rollback is real.
 */
public class NeTExPackageServiceRollbackTest extends BaseTest {

    @Autowired
    private GeneratedExportRepository generatedExportRepository;

    @Autowired
    private NeTExPublishedJourneyRepository journeyRepository;

    @Autowired
    private NeTExPublishedJourneyWriter writer;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    public void givenTrackInsertFails_whenPersisting_thenPackageJourneysAndTracksAreAllRolledBack() {
        final LocalDate today = DateProvider.dateInHelsinki();
        // The planned track column is varchar(16), so the track insert fails after the journey rows are written.
        final PublishedJourneyDraft draft = new PublishedJourneyDraft(new TrainId(1L, today),
                "FTR:ServiceJourney:rollback-test", "FTR:Line:IC", "FTR:Operator:vr", "FTR:JourneyPattern:1",
                List.of(new PublishedTrack("HKI", "x".repeat(100), 0, 0)));
        final NeTExDataset dataset = new NeTExDataset(List.of(), List.of(draft),
                PublishedJourneyWindow.of(today, 2, 14), null, null, List.of(), List.of(), List.of(), null,
                List.of());

        final NeTExService neTExService = mock(NeTExService.class);
        when(neTExService.generateNeTEx()).thenReturn(new NeTExGenerationResult(new byte[] {1, 2, 3}, Map.of(),
                List.of(today), 0, 0, 0, 0, 0, 0, 0, 0, 0, dataset));
        final NeTExPackageService service = new NeTExPackageService(neTExService, generatedExportRepository,
                writer, mock(LastUpdateService.class), transactionManager, 1, Duration.ofMillis(1));

        final long exportsBefore = generatedExportRepository.count();
        final long journeysBefore = journeyRepository.count();

        assertThrows(RuntimeException.class, service::generatePackage);

        assertEquals(exportsBefore, generatedExportRepository.count(), "the package must not be stored");
        assertEquals(journeysBefore, journeyRepository.count(), "the journeys must not be stored");
        assertEquals(0, journeyRepository
                .findByDatasetVersionAndDepartureDates(
                        java.util.Optional.ofNullable(journeyRepository.getMaxDatasetVersion()).orElse(0L) + 1,
                        List.of(today))
                .size());
    }
}
