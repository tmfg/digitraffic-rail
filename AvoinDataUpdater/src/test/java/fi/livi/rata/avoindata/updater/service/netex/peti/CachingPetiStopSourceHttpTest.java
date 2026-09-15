package fi.livi.rata.avoindata.updater.service.netex.peti;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.NonNull;
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
        // requestTimeoutSeconds is set well above blockTimeoutSeconds so existing HTTP-status-based
        // tests are unaffected by the per-attempt timeout; only the outer block(...) applies to them.
        return sourceWithExchange(exchange, 30);
    }

    private CachingPetiStopSource sourceWithExchange(final ExchangeFunction exchange, final int requestTimeoutSeconds) {
        final WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        return new CachingPetiStopSource(webClient, new PetiNeTExParser(), PETI_URL, requestTimeoutSeconds, 5);
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
        final ExchangeFunction statefulExchange = getStatefulExchange();
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

    private static @NonNull ExchangeFunction getStatefulExchange() {
        final AtomicInteger callCount = new AtomicInteger(0);
        return request -> {
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
        final AtomicInteger callCount = new AtomicInteger(0);
        final ExchangeFunction countingExchange = request -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[0])))
                    .build());
        };
        final CachingPetiStopSource source = sourceWithExchange(countingExchange);

        // when
        source.refresh();

        // then
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
        assertEquals(404, source.getLastFetchResult().httpStatus());
        // 4xx is a client error — retrying cannot help, so only one attempt is made
        assertEquals(1, callCount.get(), "4xx responses must not be retried");
    }

    // --- B4b: Transient 503 that recovers → retried automatically, no error surfaced ---

    @Test
    void givenTransientHttp503ThenSuccess_whenRefresh_thenRetriesAndSucceeds() {
        // given — first two attempts fail with 503, third succeeds
        final AtomicInteger callCount = new AtomicInteger(0);
        final ExchangeFunction flakyExchange = request -> {
            if (callCount.getAndIncrement() < 2) {
                return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                        .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[0])))
                        .build());
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(fixtureZipBytes)))
                    .build());
        };
        final CachingPetiStopSource source = sourceWithExchange(flakyExchange);

        // when
        source.refresh();

        // then — the caller never sees the transient failures, only the eventual success
        assertEquals(4, source.getStops().size());
        assertNotNull(source.getLastFetchResult());
        assertEquals("success", source.getLastFetchResult().outcome());
        assertEquals(3, callCount.get(), "expected 2 retries before success");
    }

    // --- B4c: Persistent 503 → gives up after the retry budget, keeps last-good ---

    @Test
    void givenPersistentHttp503_whenRefresh_thenGivesUpAfterRetriesAndReportsError() {
        // given — every attempt fails with 503
        final AtomicInteger callCount = new AtomicInteger(0);
        final ExchangeFunction alwaysFailingExchange = request -> {
            callCount.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[0])))
                    .build());
        };
        final CachingPetiStopSource source = sourceWithExchange(alwaysFailingExchange);

        // when
        source.refresh();

        // then — 1 initial attempt + 2 retries, then gives up
        assertTrue(source.getStops().isEmpty());
        assertNotNull(source.getLastFetchResult());
        assertEquals("error", source.getLastFetchResult().outcome());
        assertEquals(503, source.getLastFetchResult().httpStatus());
        assertEquals(3, callCount.get(), "expected the initial attempt plus 2 retries, then giving up");
    }

    // --- B4d: Transient connection error that recovers → retried automatically ---

    @Test
    void givenTransientConnectException_whenRefresh_thenRetriesAndSucceeds() {
        // given — first attempt throws a connection error, second succeeds
        final AtomicInteger callCount = new AtomicInteger(0);
        final ExchangeFunction flakyExchange = request -> {
            if (callCount.getAndIncrement() == 0) {
                return Mono.error(new ConnectException("Connection refused"));
            }
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(fixtureZipBytes)))
                    .build());
        };
        final CachingPetiStopSource source = sourceWithExchange(flakyExchange);

        // when
        source.refresh();

        // then
        assertEquals(4, source.getStops().size());
        assertNotNull(source.getLastFetchResult());
        assertEquals("success", source.getLastFetchResult().outcome());
        assertEquals(2, callCount.get(), "expected 1 retry before success");
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

    @Test
    void givenConcurrentFirstReads_whenGetStops_thenOnlyOneFetchIsPerformed() throws Exception {
        final AtomicInteger requestCount = new AtomicInteger();
        final CountDownLatch requestStarted = new CountDownLatch(1);
        final CountDownLatch releaseRequest = new CountDownLatch(1);
        final ExchangeFunction exchange = request -> Mono.fromCallable(() -> {
            requestCount.incrementAndGet();
            requestStarted.countDown();
            assertTrue(releaseRequest.await(5, TimeUnit.SECONDS));
            return ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .body(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(fixtureZipBytes)))
                    .build();
        });
        final CachingPetiStopSource source = sourceWithExchange(exchange);
        final ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            final Future<List<PetiStop>> first = executor.submit(source::getStops);
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS));
            final Future<List<PetiStop>> second = executor.submit(source::getStops);

            releaseRequest.countDown();

            assertEquals(4, first.get(5, TimeUnit.SECONDS).size());
            assertEquals(4, second.get(5, TimeUnit.SECONDS).size());
            assertEquals(1, requestCount.get());
        } finally {
            releaseRequest.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void givenFailedInitialLoad_whenGetStopsIsCalledAgain_thenRetryIsDelayed() {
        final AtomicInteger requestCount = new AtomicInteger();
        final ExchangeFunction exchange = request -> {
            requestCount.incrementAndGet();
            return Mono.error(new ConnectException("Connection refused"));
        };
        final CachingPetiStopSource source = sourceWithExchange(exchange);

        assertTrue(source.getStops().isEmpty());
        // the first call already retries transient connection errors internally (1 initial
        // attempt + 2 retries) before giving up
        assertEquals(3, requestCount.get());

        assertTrue(source.getStops().isEmpty());
        // the 1-minute initial-load retry-delay gate prevents a second call from triggering
        // another fetch (with its own internal retries) so soon after the first failure
        assertEquals(3, requestCount.get());
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
