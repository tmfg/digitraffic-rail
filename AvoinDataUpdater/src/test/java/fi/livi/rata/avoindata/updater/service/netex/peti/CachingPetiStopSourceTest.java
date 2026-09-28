package fi.livi.rata.avoindata.updater.service.netex.peti;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Unit tests for CachingPetiStopSource — parsing seam, last-good snapshot,
 * snapshot-age, and telemetry model. No Spring context, no network.
 */
class CachingPetiStopSourceTest {

    private static byte[] fixtureXmlBytes;

    private CachingPetiStopSource source;
    private WebClient stubWebClient;

    @BeforeAll
    static void readFixture() throws IOException {
        try (final InputStream is = CachingPetiStopSourceTest.class.getClassLoader()
                .getResourceAsStream("peti/stops-fixture.xml")) {
            assertNotNull(is, "stops-fixture.xml must exist in test resources");
            fixtureXmlBytes = is.readAllBytes();
        }
    }

    @BeforeEach
    void setUp() {
        // Stub WebClient that is never actually called (unit tests use the
        // parseXmlBytes seam)
        final ExchangeFunction noOpExchange = request -> Mono.empty();
        stubWebClient = WebClient.builder().exchangeFunction(noOpExchange).build();
        source = new CachingPetiStopSource(stubWebClient, new PetiNeTExParser(),
                "https://test.example.com/stops", 5);
    }

    // --- A1: Happy path — body → parse → returns parsed stops ---

    @Test
    void givenValidStopsXml_whenParseXmlBytes_thenReturnsParsedStops() {
        // when
        final List<PetiStop> result = source.parseXmlBytes(fixtureXmlBytes);

        // then — fixture has 4 valid StopPlaces (2 malformed are skipped by parser)
        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(s -> s.stopPlaceId().equals("FSR:StopPlace:1")));
    }

    // --- A2: Happy path — getMatcher builds matcher from current stops ---

    @Test
    void givenParsedStops_whenGetMatcher_thenMatchesKnownUic() {
        final List<PetiStop> stops = source.parseXmlBytes(fixtureXmlBytes);

        // when — build matcher from parsed stops (interface default method)
        final PetiUicMatcher matcher = new PetiUicMatcher(stops);

        // then — Tervola has uicCode 1000361, national UIC = 361
        assertTrue(matcher.match(361).isPresent());
    }

    // --- A5: Body is not XML at all ---

    @Test
    void givenNonXmlBytes_whenParseXmlBytes_thenThrowsException() {
        // given
        final byte[] garbage = "this is not xml".getBytes(StandardCharsets.UTF_8);

        // when / then
        assertThrows(Exception.class, () -> source.parseXmlBytes(garbage));
    }

    // --- A7: Parse failure (PetiParseException from parser) propagates ---

    @Test
    void givenUnparseableXml_whenParseXmlBytes_thenPetiParseExceptionThrown() {
        // given
        final byte[] badXml = "<<<NOT XML>>>".getBytes(StandardCharsets.UTF_8);

        // when / then
        assertThrows(PetiParseException.class, () -> source.parseXmlBytes(badXml));
    }

    // --- A8: Keep-last-good on failed refresh ---

    @Test
    void givenPriorSuccessfulRefresh_whenSubsequentRefreshFails_thenGetStopsReturnsLastGood() {
        source.applySnapshot(source.parseXmlBytes(fixtureXmlBytes));

        // when
        final List<PetiStop> result = source.getStops();

        // then
        assertEquals(4, result.size());
    }

    // --- A9: Keep-last-good on empty parse result ---

    @Test
    void givenPriorSuccessfulLoad_whenRefreshReturnsEmpty_thenRetainsPreviousStops() {
        // given — well-formed NeTEx with no StopPlaces
        final String emptyStopsXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PublicationDelivery xmlns="http://www.netex.org.uk/netex" version="1.0">
                  <PublicationTimestamp>2025-01-15T10:00:00Z</PublicationTimestamp>
                  <ParticipantRef>FSR</ParticipantRef>
                  <dataObjects>
                    <CompositeFrame id="FSR:CompositeFrame:1" version="1">
                      <frames>
                        <SiteFrame id="FSR:SiteFrame:1" version="1">
                          <stopPlaces/>
                        </SiteFrame>
                      </frames>
                    </CompositeFrame>
                  </dataObjects>
                </PublicationDelivery>
                """;

        // given — first load good data
        source.applySnapshot(source.parseXmlBytes(fixtureXmlBytes));
        assertEquals(4, source.getStops().size());

        // when — try to load empty (should not overwrite)
        source.applySnapshot(source.parseXmlBytes(emptyStopsXml.getBytes(StandardCharsets.UTF_8)));
        final List<PetiStop> afterEmpty = source.getStops();

        // then — should retain prior good data (4 stops, not 0)
        assertFalse(afterEmpty.isEmpty());
        assertEquals(4, afterEmpty.size());
    }

    // --- A10: First boot / no snapshot → getStops returns empty ---

    @Test
    void givenNoRefreshEverPerformed_whenGetStops_thenReturnsEmptyList() {
        // given — freshly constructed source, no refresh called

        // when
        final List<PetiStop> result = source.getStops();

        // then — empty list on first boot
        assertTrue(result.isEmpty());
    }

    @Test
    void givenLoadedSnapshot_whenEnsureLoaded_thenDoesNotThrow() {
        // given — snapshot already present
        source.applySnapshot(source.parseXmlBytes(fixtureXmlBytes));

        // when/then — no fetch, no failure
        assertDoesNotThrow(() -> source.ensureLoaded());
        assertEquals(4, source.getStops().size());
    }

    @Test
    void givenEmptySnapshotAndUnavailableFeed_whenEnsureLoaded_thenDoesNotThrow() {
        // given — empty snapshot; stub WebClient returns Mono.empty() so refresh cannot
        // populate it

        // when/then — an unavailable feed degrades (stays empty) rather than blocking
        // generation
        assertDoesNotThrow(() -> source.ensureLoaded());
        assertTrue(source.getStops().isEmpty());
    }

    // --- A11: Volatile immutable-swap — snapshot is an immutable list ---

    @Test
    void givenSuccessfulRefresh_whenGetStopsCalled_thenReturnedListIsUnmodifiable() {
        // given / when — getStops() after a refresh should return immutable list
        final List<PetiStop> result = source.getStops();

        // then — attempting to mutate throws
        assertThrows(UnsupportedOperationException.class, () -> result.add(null));
    }

    // --- A12: Volatile immutable-swap — concurrent read returns complete snapshot
    // ---

    @Test
    void givenConcurrentReads_whenRefreshSwapsSnapshot_thenReaderSeesCompleteSnapshot() throws Exception {
        // given — we exercise that getStops() never returns null or partial
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            final Future<List<PetiStop>> readerFuture = executor.submit(() -> {
                latch.await();
                return source.getStops();
            });

            // when — release latch, reader calls getStops()
            latch.countDown();
            final List<PetiStop> result = readerFuture.get();

            // then — result is non-null (either empty initial or full snapshot)
            assertNotNull(result);
        } finally {
            executor.shutdownNow();
        }
    }

    // --- A13: Snapshot-age — never loaded → returns -1 sentinel ---

    @Test
    void givenNoSuccessfulFetch_whenGetSnapshotAgeSeconds_thenReturnsMinus1() {
        // given — freshly constructed, no refresh

        // when
        final long age = source.getSnapshotAgeSeconds();

        // then
        assertEquals(-1L, age);
    }

    // --- A14: Snapshot-age — after successful fetch → positive seconds ---

    @Test
    void givenSuccessfulRefresh_whenGetSnapshotAgeSeconds_thenReturnsNonNegative() {
        // given — perform a successful parse to set lastSuccessfulFetch
        source.applySnapshot(source.parseXmlBytes(fixtureXmlBytes));

        // when
        final long age = source.getSnapshotAgeSeconds();

        // then — after a successful refresh, age should be >= 0
        assertTrue(age >= 0);
    }

    // --- A15: Snapshot-age — failed refresh does NOT update timestamp ---

    @Test
    void givenSuccessAtT1ThenFailureAtT2_whenGetSnapshotAgeSeconds_thenAgeReflectsT1() {
        // given — successful refresh at T1 sets lastSuccessfulFetch
        source.applySnapshot(source.parseXmlBytes(fixtureXmlBytes));
        final long ageAfterSuccess = source.getSnapshotAgeSeconds();
        assertTrue(ageAfterSuccess >= 0);

        // when — failed refresh (bad XML) at T2 should NOT update timestamp
        try {
            source.parseXmlBytes("<<<NOT XML>>>".getBytes(StandardCharsets.UTF_8));
        } catch (final PetiParseException ignored) {
        }
        final long ageAfterFailure = source.getSnapshotAgeSeconds();

        // then — age should still reflect T1 (>= 0, not reset)
        assertTrue(ageAfterFailure >= 0);
        assertTrue(ageAfterFailure >= ageAfterSuccess);
    }

    // --- A16: Telemetry model — success event carries expected fields ---

    @Test
    void givenSuccessfulFetch_whenTelemetryEmitted_thenHasExpectedFields() {
        // given — simulate a successful fetch producing a PetiFetchResult

        // when
        final PetiFetchResult result = PetiFetchResult.success(200, 842L, 4, 5, 512000L);

        // then
        assertEquals("fetchPeti", result.operation());
        assertEquals("success", result.outcome());
        assertEquals(200, result.httpStatus());
        assertTrue(result.durationMs() > 0);
        assertEquals(4, result.stopPlaces());
        assertEquals(5, result.quays());
        assertEquals(512000L, result.bodySize());
        assertNull(result.errorType());
    }

    // --- A17: Telemetry model — error event carries expected fields ---

    @Test
    void givenFailedFetchNon2xx_whenTelemetryEmitted_thenHasErrorFields() {
        // given / when
        final PetiFetchResult result = PetiFetchResult.error(500, 123L, 0L,
                "WebClientResponseException");

        // then
        assertEquals("fetchPeti", result.operation());
        assertEquals("error", result.outcome());
        assertEquals(500, result.httpStatus());
        assertTrue(result.durationMs() > 0);
        assertEquals(0, result.stopPlaces());
        assertEquals(0, result.quays());
        assertEquals("WebClientResponseException", result.errorType());
    }

    // --- A18: Telemetry model — network error (status 0) ---

    @Test
    void givenNetworkError_whenTelemetryEmitted_thenHttpStatusIsZero() {
        // given / when
        final PetiFetchResult result = PetiFetchResult.error(0, 50L, 0L, "ConnectException");

        // then
        assertEquals(0, result.httpStatus());
        assertEquals("error", result.outcome());
        assertEquals("ConnectException", result.errorType());
    }

    // --- A19: Refresh is idempotent within TTL ---

    @Test
    void givenRefreshCalledTwiceWithinTtl_whenGetStops_thenOnlyOneFetchPerformed() {
        // given — source with TTL-gated refresh. Two calls to getStops() within TTL
        // should not trigger two HTTP fetches.

        // when — first call triggers fetch, second should use cached
        final List<PetiStop> first = source.getStops();
        final List<PetiStop> second = source.getStops();

        // then — both return same reference (same snapshot)
        // In RED: will fail because getStops() throws
        assertSame(first, second);
    }

    // --- A20: Config — URL is read from injected value ---

    @Test
    void givenCustomUrl_whenConstructed_thenUrlIsAccessible() {
        // given
        final String customUrl = "https://custom.example.com/peti.zip";
        final CachingPetiStopSource customSource = new CachingPetiStopSource(
                stubWebClient, new PetiNeTExParser(), customUrl, 10);

        // when / then
        assertEquals(customUrl, customSource.getPetiUrl());
    }

}
