package fi.livi.rata.avoindata.updater.service.isuptodate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.updater.service.SimpleTransactionManager;

class LastUpdateServiceTest {

    private final WebClient webClient = mock(WebClient.class);
    private final SimpleTransactionManager simpleTransactionManager = new SimpleTransactionManager();
    private final GeneratedExportRepository generatedExportRepository = mock(GeneratedExportRepository.class);

    @Test
    void setupInitializesGtfsLastUpdatedFromLatestExport() {
        final LastUpdateService service = new LastUpdateService(webClient, simpleTransactionManager, generatedExportRepository);
        final Instant created = Instant.now();
        when(generatedExportRepository.findLatestCreatedByFileName("gtfs-passenger-stops.zip")).thenReturn(Optional.of(created));

        service.setup();

        final Instant lastUpdated = service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.GTFS);
        assertEquals(created, lastUpdated);
    }

    @Test
    void setupLeavesGtfsLastUpdatedUnsetWhenNoExportExists() {
        final LastUpdateService service = new LastUpdateService(webClient, simpleTransactionManager, generatedExportRepository);
        when(generatedExportRepository.findLatestCreatedByFileName("gtfs-passenger-stops.zip")).thenReturn(Optional.empty());

        service.setup();

        assertFalse(service.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.GTFS));
    }
}

