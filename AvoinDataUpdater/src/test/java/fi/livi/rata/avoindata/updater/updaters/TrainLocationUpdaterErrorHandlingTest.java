package fi.livi.rata.avoindata.updater.updaters;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.updater.deserializers.PalaYksikkoDeserializer;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenTrainLocationFilter;
import fi.livi.rata.avoindata.updater.service.trainlocation.TrainLocationNearTrackFilterService;

/**
 * Verifies how {@link TrainLocationUpdater} maps a PALA fetch outcome onto the wide-event log's
 * {@code rail.upstream.pala.http_status} field — the signal the PALA availability SLI relies on.
 *
 * <p>Guards the {@code fetchFromPala} catch logic (paired with {@code RipaServiceTest}, which pins the WebClient
 * behavior that produces the exception): an HTTP error must record the real status code, a transport/network failure
 * must record {@code 0}, and a success must record {@code 200}. If the exception handling regresses, these tests fail.
 *
 * <p>Pure unit test — collaborators are mocked and the updater is called directly, so no Spring context or DB is needed.
 * The wide-event line is captured with a Logback {@link ListAppender}.
 */
public class TrainLocationUpdaterErrorHandlingTest {

    private TrainLocationUpdater updater;
    private RipaService ripaService;
    private PalaYksikkoDeserializer deserializer;
    private RecentlySeenTrainLocationFilter recentlySeenFilter;

    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    public void setUp() {
        updater = new TrainLocationUpdater();
        ripaService = mock(RipaService.class);
        deserializer = mock(PalaYksikkoDeserializer.class);
        recentlySeenFilter = mock(RecentlySeenTrainLocationFilter.class);

        ReflectionTestUtils.setField(updater, "ripaService", ripaService);
        ReflectionTestUtils.setField(updater, "palaYksikkoDeserializer", deserializer);
        ReflectionTestUtils.setField(updater, "recentlySeenTrainLocationFilter", recentlySeenFilter);
        ReflectionTestUtils.setField(updater, "trainLocationNearTrackFilterService",
                mock(TrainLocationNearTrackFilterService.class));
        ReflectionTestUtils.setField(updater, "mqttPublishService", mock(MQTTPublishService.class));
        ReflectionTestUtils.setField(updater, "trainLocationRepository", mock(TrainLocationRepository.class));
        ReflectionTestUtils.setField(updater, "lastUpdateService", mock(LastUpdateService.class));

        logbackLogger = (Logger) LoggerFactory.getLogger(TrainLocationUpdater.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    public void tearDown() {
        logbackLogger.detachAppender(appender);
    }

    @Test
    public void httpErrorFromPalaShouldRecordRealStatusCode() {
        when(ripaService.getFromPalaAsString(anyString()))
                .thenThrow(new WebClientResponseException(503, "Service Unavailable", HttpHeaders.EMPTY, null, null));

        updater.trainLocation();

        assertWideLogContains("outcome=error", "rail.upstream.pala.http_status=503");
    }

    @Test
    public void networkFailureShouldRecordStatusZero() {
        // A non-WebClientResponseException (e.g. connection refused / DNS / timeout) must NOT be mistaken for an
        // HTTP status — fetchFromPala leaves rail.upstream.pala.http_status=0 for these.
        when(ripaService.getFromPalaAsString(anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        updater.trainLocation();

        assertWideLogContains("outcome=error", "rail.upstream.pala.http_status=0");
    }

    @Test
    public void successfulFetchShouldRecordStatus200() {
        when(ripaService.getFromPalaAsString(anyString())).thenReturn("{}");
        when(deserializer.deserialize(anyString())).thenReturn(List.of());
        when(recentlySeenFilter.filter(anyList())).thenReturn(List.of());

        updater.trainLocation();

        assertWideLogContains("outcome=success", "rail.upstream.pala.http_status=200");
    }

    private void assertWideLogContains(final String... expectedSubstrings) {
        final boolean found = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(msg -> Arrays.stream(expectedSubstrings).allMatch(msg::contains));
        assertTrue(found, "expected a log line containing all of " + Arrays.toString(expectedSubstrings)
                + " but got: " + appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }
}
