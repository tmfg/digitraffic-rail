package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService.LastUpdatedType;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExGenerationResult;

/**
 * The NETEX last-updated entry must move only after the package and its journeys were stored, so the
 * monitoring does not report a failed or empty run as fresh.
 */
class NeTExPackageServiceLastUpdatedTest {

    private NeTExService neTExService;
    private NeTExPublishedJourneyWriter publishedJourneyWriter;
    private LastUpdateService lastUpdateService;
    private NeTExPackageService service;

    @BeforeEach
    void setUp() {
        neTExService = mock(NeTExService.class);
        publishedJourneyWriter = mock(NeTExPublishedJourneyWriter.class);
        lastUpdateService = mock(LastUpdateService.class);
        service = new NeTExPackageService(neTExService, mock(GeneratedExportRepository.class),
                publishedJourneyWriter, lastUpdateService, mock(PlatformTransactionManager.class), 1,
                Duration.ofMillis(1));
    }

    private NeTExGenerationResult packageResult() {
        return new NeTExGenerationResult(new byte[] { 1, 2, 3 }, Map.of(), List.of(LocalDate.now()), 0, 0, 0, 0, 0,
                0, 0, 0, 0, null);
    }

    @Test
    void givenPackagePersisted_whenGenerating_thenNetexIsMarkedUpdated() {
        when(neTExService.generateNeTEx()).thenReturn(packageResult());

        service.generatePackage();

        verify(publishedJourneyWriter).persistWindow(any());
        verify(lastUpdateService).update(LastUpdatedType.NETEX);
    }

    @Test
    void givenNoTimetableData_whenGenerating_thenNetexIsNotMarkedUpdated() {
        when(neTExService.generateNeTEx()).thenReturn(null);

        service.generatePackage();

        verify(lastUpdateService, never()).update(any(LastUpdatedType.class));
    }

    @Test
    void givenPersistFails_whenGenerating_thenNetexIsNotMarkedUpdated() {
        when(neTExService.generateNeTEx()).thenReturn(packageResult());
        doThrow(new IllegalStateException("insert failed")).when(publishedJourneyWriter).persistWindow(any());

        assertThrows(IllegalStateException.class, service::generatePackage);

        verify(lastUpdateService, never()).update(any(LastUpdatedType.class));
    }
}
