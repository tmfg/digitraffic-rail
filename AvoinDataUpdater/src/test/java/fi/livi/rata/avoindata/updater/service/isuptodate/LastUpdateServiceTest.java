package fi.livi.rata.avoindata.updater.service.isuptodate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.rata.avoindata.common.dao.LastUpdateRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.common.LastUpdate;
import fi.livi.rata.avoindata.updater.service.SimpleTransactionManager;

class LastUpdateServiceTest {

    private final WebClient webClient = mock(WebClient.class);
    private final SimpleTransactionManager simpleTransactionManager = new SimpleTransactionManager();
    private final GeneratedExportRepository generatedExportRepository = mock(GeneratedExportRepository.class);

    private final LastUpdateRepository lastUpdateRepository = mock(LastUpdateRepository.class);

    private LastUpdateService newService() {
        return newService(false);
    }

    private LastUpdateService newService(final boolean siriSxEnabled) {
        return new LastUpdateService(webClient, simpleTransactionManager, generatedExportRepository,
                lastUpdateRepository, false, false, siriSxEnabled);
    }

    @Test
    void setupInitializesGtfsLastUpdatedFromLatestExport() {
        final LastUpdateService service = newService();
        final Instant created = Instant.now();
        when(generatedExportRepository.findLatestCreatedByFileName("gtfs-passenger-stops.zip")).thenReturn(Optional.of(created));

        service.setup();

        final Instant lastUpdated = service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.GTFS);
        assertEquals(created, lastUpdated);
    }

    @Test
    void setupListsEveryDataTypeFromTheStart() {
        final LastUpdateService service = newService();
        when(generatedExportRepository.findLatestCreatedByFileName(any())).thenReturn(Optional.empty());

        service.setup();

        for (final LastUpdateService.LastUpdatedType type : LastUpdateService.LastUpdatedType.values()) {
            if (!type.name().startsWith("SIRI_")) {
                assertTrue(service.getLastUpdateTimes().containsKey(type), type.name());
            }
        }
    }

    @Test
    void setupSkipsSiriTypesWhenSiriIsNotEnabled() {
        final LastUpdateService service = newService();
        when(generatedExportRepository.findLatestCreatedByFileName(any())).thenReturn(Optional.empty());

        service.setup();

        assertFalse(service.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.SIRI_ET));
        assertFalse(service.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS));
    }

    @Test
    void setupInitializesSiriFromLatestExportWhenEnabled() {
        final LastUpdateService service = newService(true);
        final Instant created = Instant.now().minusSeconds(100);
        when(generatedExportRepository.findLatestCreatedByFileName(any())).thenReturn(Optional.empty());
        when(generatedExportRepository.findLatestCreatedByFileName("siri-sx.xml")).thenReturn(Optional.of(created));

        service.setup();

        assertEquals(created, service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS));
        assertTrue(service.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS));
    }

    @Test
    void setupLeavesGtfsLastUpdatedAtStartTimeWhenNoExportExists() {
        final LastUpdateService service = newService();
        when(generatedExportRepository.findLatestCreatedByFileName("gtfs-passenger-stops.zip")).thenReturn(Optional.empty());

        service.setup();

        assertTrue(service.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.GTFS));
    }

    @Test
    void setupReadsStoredTimeOfDailyTypes() {
        final LastUpdateService service = newService();
        final Instant stored = Instant.now().minusSeconds(3600 * 10);
        when(generatedExportRepository.findLatestCreatedByFileName(any())).thenReturn(Optional.empty());
        when(lastUpdateRepository.findAll()).thenReturn(List.of(
                new LastUpdate("STATIONS", stored),
                new LastUpdate("TRAINS", stored),
                new LastUpdate("NO_SUCH_TYPE", stored)));

        service.setup();

        assertEquals(stored, service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.STATIONS));
        // Real time types are not read from the database
        assertTrue(service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.TRAINS).isAfter(stored));
    }

    @Test
    void updateStoresDailyTypesInDatabase() {
        final LastUpdateService service = newService();
        when(generatedExportRepository.findLatestCreatedByFileName(any())).thenReturn(Optional.empty());
        service.setup();

        service.update(LastUpdateService.LastUpdatedType.STATIONS);
        service.update("operators");

        verify(lastUpdateRepository).save(org.mockito.ArgumentMatchers.argThat(
                (LastUpdate u) -> u.type.equals("STATIONS") && u.lastUpdated != null));
        verify(lastUpdateRepository).save(org.mockito.ArgumentMatchers.argThat(
                (LastUpdate u) -> u.type.equals("OPERATORS")));
    }

    @Test
    void updateDoesNotStoreRealTimeTypesInDatabase() {
        final LastUpdateService service = newService();

        service.update(LastUpdateService.LastUpdatedType.TRAINS);
        service.update(LastUpdateService.LastUpdatedType.SIRI_ET);

        verify(lastUpdateRepository, never()).save(any());
    }

    @Test
    void updateKeepsTimeInMemoryWhenStoringFails() {
        final LastUpdateService service = newService();
        when(lastUpdateRepository.save(any())).thenThrow(new RuntimeException("db down"));

        service.update(LastUpdateService.LastUpdatedType.STATIONS);

        assertTrue(service.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.STATIONS));
    }
}
