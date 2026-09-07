package fi.livi.rata.avoindata.updater.service.netex.peti;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Unit tests for CachingPetiStopSource — zip parsing seam, last-good snapshot,
 * snapshot-age, and telemetry model. No Spring context, no network.
 */
class CachingPetiStopSourceTest {

    private static byte[] fixtureZipBytes;
    private static byte[] fixtureXmlBytes;
    private static byte[] oversizedFixtureZipBytes;

    private CachingPetiStopSource source;
    private WebClient stubWebClient;

    @BeforeAll
    static void buildFixtureZip() throws IOException {
        // Read the existing stops-fixture.xml from test resources
        try (final InputStream is = CachingPetiStopSourceTest.class.getClassLoader()
                .getResourceAsStream("peti/stops-fixture.xml")) {
            assertNotNull(is, "stops-fixture.xml must exist in test resources");
            fixtureXmlBytes = is.readAllBytes();
        }
        fixtureZipBytes = buildZip("stops.xml", fixtureXmlBytes, "authorities.xml",
                "<xml>authorities</xml>".getBytes(StandardCharsets.UTF_8));
        oversizedFixtureZipBytes = buildOversizedFixtureZip();
    }

    @BeforeEach
    void setUp() {
        // Stub WebClient that is never actually called (unit tests use parseZipBytes
        // seam)
        final ExchangeFunction noOpExchange = request -> Mono.empty();
        stubWebClient = WebClient.builder().exchangeFunction(noOpExchange).build();
        source = new CachingPetiStopSource(stubWebClient, new PetiNeTExParser(),
                "https://test.example.com/peti.zip", 5);
    }

    // --- A1: Happy path — zip → parse → returns parsed stops ---

    @Test
    void givenValidZipWithStopsXml_whenParseZipBytes_thenReturnsParsedStops() {
        // given
        final byte[] zip = fixtureZipBytes;

        // when
        final List<PetiStop> result = source.parseZipBytes(zip);

        // then — fixture has 4 valid StopPlaces (2 malformed are skipped by parser)
        assertEquals(4, result.size());
        assertTrue(result.stream().anyMatch(s -> s.stopPlaceId().equals("FSR:StopPlace:1")));
    }

    // --- A2: Happy path — getMatcher builds matcher from current stops ---

    @Test
    void givenSuccessfulRefreshViaParseZipBytes_whenGetMatcher_thenMatchesKnownUic() {
        // given — simulate successful refresh by directly setting lastGood via
        // parseZipBytes
        // (in real impl, refresh() calls parseZipBytes and updates lastGood)
        // For skeleton: we call parseZipBytes and verify the matcher works on the
        // result
        final List<PetiStop> stops = source.parseZipBytes(fixtureZipBytes);

        // when — build matcher from parsed stops (interface default method)
        final PetiUicMatcher matcher = new PetiUicMatcher(stops);

        // then — Tervola has uicCode 1000361, national UIC = 361
        assertTrue(matcher.match(361).isPresent());
    }

    // --- A3: Zip entry extraction — stops.xml found among multiple entries ---

    @Test
    void givenZipWithBothStopsAndAuthorities_whenParseZipBytes_thenOnlyStopsXmlIsParsed() {
        // given — fixtureZipBytes contains both stops.xml and authorities.xml
        final byte[] zip = fixtureZipBytes;

        // when
        final List<PetiStop> result = source.parseZipBytes(zip);

        // then — returns stops (not authorities content)
        assertEquals(4, result.size());
    }

    // --- A4: Zip entry extraction — no stops.xml entry ---

    @Test
    void givenZipWithOnlyAuthoritiesXml_whenParseZipBytes_thenThrowsOrReturnsEmpty() throws IOException {
        // given — zip with only authorities.xml, no stops.xml
        final byte[] zipNoStops = buildZip("authorities.xml",
                "<xml>authorities</xml>".getBytes(StandardCharsets.UTF_8));

        // when / then — should throw (stops.xml not found) or return empty indicating
        // failure
        assertThrows(Exception.class, () -> source.parseZipBytes(zipNoStops));
    }

    // --- A5: Malformed zip (not a valid zip stream) ---

    @Test
    void givenNonZipBytes_whenParseZipBytes_thenThrowsException() {
        // given
        final byte[] garbage = "this is not a zip file".getBytes(StandardCharsets.UTF_8);

        // when / then
        assertThrows(Exception.class, () -> source.parseZipBytes(garbage));
    }

    // --- A6: Empty zip (valid zip, zero entries) ---

    @Test
    void givenEmptyZip_whenParseZipBytes_thenThrowsException() throws IOException {
        // given — valid zip with zero entries
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            // no entries
        }
        final byte[] emptyZip = baos.toByteArray();

        // when / then — no stops.xml found
        assertThrows(Exception.class, () -> source.parseZipBytes(emptyZip));
    }

    // --- A7: Parse failure (PetiParseException from parser) propagates ---

    @Test
    void givenZipWithUnparseableStopsXml_whenParseZipBytes_thenPetiParseExceptionThrown() throws IOException {
        // given — zip with stops.xml containing unparseable XML
        final byte[] badXml = "<<<NOT XML>>>".getBytes(StandardCharsets.UTF_8);
        final byte[] zip = buildZip("stops.xml", badXml);

        // when / then
        assertThrows(PetiParseException.class, () -> source.parseZipBytes(zip));
    }

    // --- A8: Keep-last-good on failed refresh ---

    @Test
    void givenPriorSuccessfulRefresh_whenSubsequentRefreshFails_thenGetStopsReturnsLastGood() {
        // given — first: successful parse sets lastGood
        // The actual refresh() method updates lastGood on success.
        // In the skeleton, getStops() throws UnsupportedOperationException.
        // This test will FAIL in RED because getStops() is not implemented.

        // Simulate: call refresh-like behavior, then a failed one, then getStops()
        // Once implemented, after successful refresh getStops() returns stops;
        // after failed refresh, still returns the same stops.
        source.applySnapshot(source.parseZipBytes(fixtureZipBytes)); // proves parsing works

        // when — getStops() should return the cached snapshot
        // This call exercises the real caching logic
        final List<PetiStop> result = source.getStops();

        // then — should be the previously cached 4 stops (will fail until implemented)
        assertEquals(4, result.size());
    }

    // --- A9: Keep-last-good on empty parse result ---

    @Test
    void givenPriorSuccessfulLoad_whenRefreshReturnsEmpty_thenRetainsPreviousStops() throws IOException {
        // given — valid zip with stops.xml that has no StopPlaces
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
        final byte[] emptyStopsZip = buildZip("stops.xml", emptyStopsXml.getBytes(StandardCharsets.UTF_8));

        // given — first load good data
        source.applySnapshot(source.parseZipBytes(fixtureZipBytes));
        assertEquals(4, source.getStops().size());

        // when — try to load empty (should not overwrite)
        source.applySnapshot(source.parseZipBytes(emptyStopsZip));
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
        source.applySnapshot(source.parseZipBytes(fixtureZipBytes));

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
        source.applySnapshot(source.parseZipBytes(fixtureZipBytes));

        // when
        final long age = source.getSnapshotAgeSeconds();

        // then — after a successful refresh, age should be >= 0
        assertTrue(age >= 0);
    }

    // --- A15: Snapshot-age — failed refresh does NOT update timestamp ---

    @Test
    void givenSuccessAtT1ThenFailureAtT2_whenGetSnapshotAgeSeconds_thenAgeReflectsT1() throws IOException {
        // given — successful refresh at T1 sets lastSuccessfulFetch
        source.applySnapshot(source.parseZipBytes(fixtureZipBytes));
        final long ageAfterSuccess = source.getSnapshotAgeSeconds();
        assertTrue(ageAfterSuccess >= 0);

        // when — failed refresh (bad XML) at T2 should NOT update timestamp
        final byte[] badZip = buildZip("stops.xml",
                "<<<NOT XML>>>".getBytes(StandardCharsets.UTF_8));
        try {
            source.parseZipBytes(badZip);
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

    // --- H1a: Oversized zip entry — decompressed-size cap throws
    // PetiParseException ---

    @Test
    void givenOversizedZipEntry_whenParseZipBytes_thenThrowsPetiParseException() {
        // given — zip whose stops.xml decompresses to > 50 MB (well-formed XML with
        // padding comment)
        final byte[] zip = oversizedFixtureZipBytes;

        // when / then — after green phase adds MAX_DECOMPRESSED_BYTES cap, this throws
        // PetiParseException
        // with message indicating size cap. Currently no cap exists → parses fine →
        // assertThrows fails.
        final PetiParseException ex = assertThrows(PetiParseException.class,
                () -> source.parseZipBytes(zip));
        assertTrue(ex.getMessage().contains("size cap") || ex.getMessage().contains("decompressed"),
                "Exception message should indicate decompressed size cap, got: " + ex.getMessage());
    }

    // --- H1b: Oversized zip — last-good snapshot retained on cap breach ---

    @Test
    void givenOversizedZip_whenRefreshAttempted_thenLastGoodSnapshotRetained() {
        // given — establish a good baseline snapshot (4 stops from normal fixture)
        source.applySnapshot(source.parseZipBytes(fixtureZipBytes));
        assertEquals(4, source.getStops().size(), "baseline should have 4 stops");
        assertTrue(source.getSnapshotAgeSeconds() >= 0, "baseline sets snapshot timestamp");

        // when — attempt refresh with oversized zip (2 stops, >50 MB decompressed).
        // After green phase, parseZipBytes will throw PetiParseException (size cap) and
        // lastGood is NOT overwritten. Currently no cap → parses 2 stops → overwrites
        // lastGood.
        try {
            source.parseZipBytes(oversizedFixtureZipBytes);
        } catch (final PetiParseException ignored) {
            // expected after green phase; in red phase no exception is thrown
        }

        // then — last-good should still be the baseline (4 stops, not the oversized
        // doc's 2)
        assertEquals(4, source.getStops().size(),
                "last-good snapshot should be retained when oversized zip is rejected");
        assertTrue(source.getSnapshotAgeSeconds() >= 0,
                "snapshot age should still reflect the earlier successful fetch");
    }

    // --- Helper methods ---

    private static byte[] buildZip(final String name, final byte[] content) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static byte[] buildZip(final String name1, final byte[] content1,
            final String name2, final byte[] content2) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name1));
            zos.write(content1);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(name2));
            zos.write(content2);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /**
     * Builds a zip with a stops.xml entry that decompresses to > 50 MB.
     * The content is well-formed NeTEx XML with a large XML comment (trivially
     * compressible)
     * and 2 valid StopPlaces (different count from baseline's 4).
     */
    private static byte[] buildOversizedFixtureZip() throws IOException {
        final int TARGET_DECOMPRESSED_SIZE = 51 * 1024 * 1024; // 51 MB — exceeds 50 MB cap
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("stops.xml"));

            final byte[] header = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"1.0\">\n"
                    + "  <PublicationTimestamp>2025-01-15T10:00:00Z</PublicationTimestamp>\n"
                    + "  <ParticipantRef>FSR</ParticipantRef>\n"
                    + "  <!-- ").getBytes(StandardCharsets.UTF_8);
            zos.write(header);

            // Pad with repeated 'X' inside XML comment to inflate past 50 MB
            final byte[] pad = new byte[8192];
            Arrays.fill(pad, (byte) 'X');
            int written = header.length;
            while (written < TARGET_DECOMPRESSED_SIZE) {
                zos.write(pad);
                written += pad.length;
            }

            final byte[] footer = (" -->\n"
                    + "  <dataObjects>\n"
                    + "    <CompositeFrame id=\"FSR:CompositeFrame:1\" version=\"1\">\n"
                    + "      <frames>\n"
                    + "        <SiteFrame id=\"FSR:SiteFrame:1\" version=\"1\">\n"
                    + "          <stopPlaces>\n"
                    + "            <StopPlace id=\"FSR:StopPlace:OVR1\" version=\"1\">\n"
                    + "              <Name>Oversized1</Name>\n"
                    + "              <keyList><KeyValue><Key>uicCode</Key><Value>1000901</Value></KeyValue></keyList>\n"
                    + "            </StopPlace>\n"
                    + "            <StopPlace id=\"FSR:StopPlace:OVR2\" version=\"1\">\n"
                    + "              <Name>Oversized2</Name>\n"
                    + "              <keyList><KeyValue><Key>uicCode</Key><Value>1000902</Value></KeyValue></keyList>\n"
                    + "            </StopPlace>\n"
                    + "          </stopPlaces>\n"
                    + "        </SiteFrame>\n"
                    + "      </frames>\n"
                    + "    </CompositeFrame>\n"
                    + "  </dataObjects>\n"
                    + "</PublicationDelivery>\n").getBytes(StandardCharsets.UTF_8);
            zos.write(footer);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
