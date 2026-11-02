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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.rata.avoindata.common.dao.LastUpdateRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.common.LastUpdate;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.SimpleTransactionManager;
import reactor.core.publisher.Mono;

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

    @Test
    void monthlyDumpOfPreviousMonthIsUsedAfterTheFifth() {
        assertEquals("2026-02-01", trainsDumpMonthOn(LocalDate.of(2026, 3, 6)));
    }

    @Test
    void monthlyDumpOfMonthBeforePreviousIsUsedUntilTheFifth() {
        assertEquals("2026-01-01", trainsDumpMonthOn(LocalDate.of(2026, 3, 5)));
    }

    @Test
    void monthlyDumpMonthWorksOverNewYear() {
        assertEquals("2025-12-01", trainsDumpMonthOn(LocalDate.of(2026, 1, 6)));
        assertEquals("2025-11-01", trainsDumpMonthOn(LocalDate.of(2026, 1, 5)));
    }

    @Test
    void monthlyDumpTimeIsTheLastModifiedOfTheDump() {
        final ZonedDateTime modified = ZonedDateTime.of(2026, 3, 5, 10, 15, 30, 0, ZoneOffset.UTC);
        final LastUpdateService service = newServiceWithWebClient(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(modified)).build()));

        updateAt(service, LocalDate.of(2026, 3, 10));

        assertEquals(modified.toInstant(), service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.TRAINS_DUMP));
        assertEquals(modified.toInstant(), service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.COMPOSITIONS_DUMP));
    }

    @Test
    void monthlyDumpTimeStaysWhenTheRequestFails() {
        final LastUpdateService service = newServiceWithWebClient(request -> Mono.error(new IllegalStateException("fail")));
        service.setup();
        final Instant before = service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.TRAINS_DUMP);

        updateAt(service, LocalDate.of(2026, 3, 10));

        assertEquals(before, service.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.TRAINS_DUMP));
    }

    private String trainsDumpMonthOn(final LocalDate today) {
        final List<String> urls = new ArrayList<>();
        final LastUpdateService service = newServiceWithWebClient(request -> {
            urls.add(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        });

        updateAt(service, today);

        final String url = urls.stream().filter(u -> u.contains("/trains/dumps/")).findFirst().orElseThrow();
        return url.substring(url.lastIndexOf("trains-") + "trains-".length(), url.length() - ".zip".length());
    }

    private LastUpdateService newServiceWithWebClient(final ExchangeFunction exchange) {
        return new LastUpdateService(WebClient.builder().exchangeFunction(exchange).build(), simpleTransactionManager,
                generatedExportRepository, lastUpdateRepository, false, false, false);
    }

    private static void updateAt(final LastUpdateService service, final LocalDate today) {
        try (final MockedStatic<DateProvider> dateProvider = Mockito.mockStatic(DateProvider.class)) {
            dateProvider.when(DateProvider::dateInHelsinki).thenReturn(today);
            service.updateLastUpdateTimesScheduled();
        }
    }
}
