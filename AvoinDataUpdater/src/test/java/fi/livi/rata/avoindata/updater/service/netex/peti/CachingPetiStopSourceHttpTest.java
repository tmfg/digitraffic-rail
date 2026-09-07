package fi.livi.rata.avoindata.updater.service.netex.peti;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * HTTP-level tests for CachingPetiStopSource using ExchangeFunction stubs.
 * Mirrors the RipaServiceTest pattern: stubs control HTTP responses, exercises
 * the full refresh() → WebClient.retrieve().bodyToMono(byte[]).block() code path.
 */
class CachingPetiStopSourceHttpTest {

    private static byte[] fixtureZipBytes;
    private static final String PETI_URL = "https://rae-test.fintraffic.fi/exports/PETI-rail-NeTEx.zip";

    @BeforeAll
    static void buildFixtureZip() throws IOException {
        try (final InputStream is = CachingPetiStopSourceHttpTest.class.getClassLoader()
                .getResourceAsStream("peti/stops-fixture.xml")) {
            assertNotNull(is, "stops-fixture.xml must exist in test resources");
            final byte[] xmlBytes = is.readAllBytes();
            fixtureZipBytes = buildZipWithStopsAndAuthorities(xmlBytes);
        }
    }

    private CachingPetiStopSource sourceWithExchange(final ExchangeFunction exchange) {
        final WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new CachingPetiStopSource(webClient, new PetiNeTExParser(), PETI_URL, 5);
    }

    private static ExchangeFunction exchangeReturning(final HttpStatus status, final byte[] body) {
        return request -> Mono.just(
                ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(body)))
                        .build());
    }

    // --- B1: HTTP 200 with valid zip body → successful refresh ---

    @Test
    void givenHttp200WithValidZip_whenRefresh_thenGetStopsReturnsParsedStops() {
        // given
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.OK, fixtureZipBytes));

        // when — trigger refresh (which fetches via WebClient)
        source.refresh();

        // then
        final List<PetiStop> stops = source.getStops();
        assertEquals(4, stops.size());
    }

    // --- B2: HTTP 500 → keeps last-good, telemetry outcome=error ---

    @Test
    void givenPriorSuccessThenHttp500_whenRefresh_thenKeepsLastGoodAndTelemetryIsError() {
        // given — first: successful fetch, then second call returns 500
        final java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final ExchangeFunction statefulExchange = request -> {
            if (callCount.getAndIncrement() == 0) {
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(fixtureZipBytes)))
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[0])))
                    .build());
        };
        final CachingPetiStopSource source = sourceWithExchange(statefulExchange);
        source.refresh();
        assertEquals(4, source.getStops().size());

        // when — second: 500 response
        source.refresh();

        // then — getStops() should still return 4 stops (last-good preserved)
        assertEquals(4, source.getStops().size());
        // telemetry should report error
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
        assertEquals(500, source.getLastFetchResult().httpStatus());
    }

    // --- B3: HTTP 503 (Service Unavailable) → same as 500 ---

    @Test
    void givenHttp503_whenRefresh_thenKeepsLastGoodAndReportsError() {
        // given
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.SERVICE_UNAVAILABLE, new byte[0]));

        // when
        source.refresh();

        // then — no prior snapshot, stays empty; telemetry shows error
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
        assertEquals(503, source.getLastFetchResult().httpStatus());
    }

    // --- B4: HTTP 404 → keeps last-good, telemetry outcome=error ---

    @Test
    void givenHttp404_whenRefresh_thenKeepsLastGoodAndReportsError() {
        // given
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.NOT_FOUND, new byte[0]));

        // when
        source.refresh();

        // then
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
        assertEquals(404, source.getLastFetchResult().httpStatus());
    }

    // --- B5: Network error (ExchangeFunction throws) → keeps last-good ---

    @Test
    void givenNetworkError_whenRefresh_thenKeepsLastGoodAndReportsStatusZero() {
        // given
        final ExchangeFunction failingExchange = request ->
                Mono.error(new ConnectException("Connection refused"));
        final CachingPetiStopSource source = sourceWithExchange(failingExchange);

        // when
        source.refresh();

        // then
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
        assertEquals(0, source.getLastFetchResult().httpStatus());
        assertEquals("ConnectException", source.getLastFetchResult().errorType());
    }

    // --- B6: Timeout (block duration exceeded) → keeps last-good ---

    @Test
    void givenTimeout_whenRefresh_thenKeepsLastGoodAndReportsError() {
        // given — Mono.never() simulates a hang; block() will timeout
        final ExchangeFunction hangingExchange = request -> Mono.never();
        final CachingPetiStopSource source = sourceWithExchange(hangingExchange);

        // when
        source.refresh();

        // then — previous snapshot (empty on first boot) preserved
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
    }

    // --- B7: HTTP 200 but body is not a valid zip → keeps last-good ---

    @Test
    void givenHttp200WithNonZipBody_whenRefresh_thenKeepsLastGoodAndReportsError() {
        // given
        final byte[] notAZip = "hello world".getBytes(StandardCharsets.UTF_8);
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.OK, notAZip));

        // when
        source.refresh();

        // then
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
    }

    // --- B8: HTTP 200 with valid zip but invalid XML in stops.xml → keeps last-good ---

    @Test
    void givenHttp200WithInvalidXmlInZip_whenRefresh_thenKeepsLastGoodAndReportsError() throws IOException {
        // given
        final byte[] badXml = "<<<NOT VALID XML>>>".getBytes(StandardCharsets.UTF_8);
        final byte[] badZip = buildZip("stops.xml", badXml);
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.OK, badZip));

        // when
        source.refresh();

        // then
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
    }

    // --- B9: First fetch failure (no prior snapshot) → getStops remains empty ---

    @Test
    void givenNoPriorSnapshot_whenFirstFetchFails_thenGetStopsReturnsEmpty() {
        // given — source with 500 response, never had a successful fetch
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.INTERNAL_SERVER_ERROR, new byte[0]));

        // when
        source.refresh();

        // then
        final List<PetiStop> result = source.getStops();
        assertTrue(result.isEmpty());
    }

    // --- B10: Duration is measured and reported in telemetry ---

    @Test
    void givenSuccessfulFetch_whenRefreshCompletes_thenDurationIsNonNegative() {
        // given
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.OK, fixtureZipBytes));

        // when
        source.refresh();

        // then
        assertNotNull(source.getLastFetchResult());
        assertTrue(source.getLastFetchResult().durationMs() >= 0);
    }

    // --- B11: Response body size is reported in telemetry ---

    @Test
    void givenSuccessfulFetch_whenRefreshCompletes_thenBodySizeMatchesPayload() {
        // given
        final CachingPetiStopSource source = sourceWithExchange(
                exchangeReturning(HttpStatus.OK, fixtureZipBytes));

        // when
        source.refresh();

        // then
        assertNotNull(source.getLastFetchResult());
        assertEquals(fixtureZipBytes.length, source.getLastFetchResult().bodySize());
    }

    // --- Helper methods ---

    private static byte[] buildZipWithStopsAndAuthorities(final byte[] stopsXmlBytes) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("stops.xml"));
            zos.write(stopsXmlBytes);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("authorities.xml"));
            zos.write("<xml>authorities</xml>".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static byte[] buildZip(final String name, final byte[] content) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(content);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
