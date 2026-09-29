package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.Exceptions;
import reactor.util.retry.Retry;

/**
 * HTTP-backed PetiStopSource that fetches the PETI rail stops as NeTEx XML, parses it with
 * PetiNeTExParser, and caches the result as a last-good snapshot.
 *
 * <p>
 * Refreshes on schedule (02:00 UTC, two hours before the 04:00 UTC NeTEx generation), with an hourly
 * safety-net retry ({@link #retryIfLastFailed()})
 * so a failed daily refresh doesn't leave the snapshot stale for a full day. {@link #getStops()}
 * also loads on demand if the snapshot is still empty, so generation never depends on the daily
 * fetch having run yet in this JVM (e.g. right after a restart).
 *
 * <p>
 * Transient failures (5xx responses, connection errors, per-attempt timeouts) are retried with a
 * short exponential backoff before giving up; 4xx responses and parse errors are not retried,
 * since retrying them cannot succeed.
 * On fetch/parse failure, the last-good snapshot is preserved — generation continues with stale
 * but valid data rather than empty/partial.
 */
@Component
@ConditionalOnProperty(name = "updater.netex.peti.enabled", havingValue = "true", matchIfMissing = true)
public class CachingPetiStopSource implements PetiStopSource {

    private static final Logger log = LoggerFactory.getLogger(CachingPetiStopSource.class);
    private static final Duration INITIAL_LOAD_RETRY_DELAY = Duration.ofMinutes(1);

    /** Retries for transient failures (5xx / connection / timeout) within a single fetch attempt. */
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(4);

    private final WebClient webClient;
    private final PetiNeTExParser parser;
    private final String petiUrl;
    private final Duration requestTimeout;
    private final Duration blockTimeout;
    private final Object refreshLock = new Object();
    private Instant nextInitialLoadAttempt = Instant.MIN;

    private volatile List<PetiStop> lastGood = List.of();
    private volatile Instant lastSuccessfulFetch = null;
    private volatile PetiFetchResult lastFetchResult = null;

    public CachingPetiStopSource(
            final WebClient webClient,
            final PetiNeTExParser parser,
            final @Value("${updater.netex.peti.url}") String petiUrl,
            final @Value("${updater.netex.peti.request-timeout-seconds:10}") int requestTimeoutSeconds,
            final @Value("${updater.netex.peti.block-timeout-seconds:40}") int blockTimeoutSeconds) {
        this.webClient = webClient;
        this.parser = parser;
        this.petiUrl = petiUrl;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.blockTimeout = Duration.ofSeconds(blockTimeoutSeconds);
    }

    @Override
    public List<PetiStop> getStops() {
        ensureLoaded();
        return lastGood;
    }

    /**
     * Loads the snapshot on demand when empty, so generation never depends on the daily
     * warm-up having run in this JVM (e.g. after a restart or an early manual run). When the
     * feed is unavailable, generation degrades to a package without stop assignments rather
     * than failing outright.
     */
    private void ensureLoaded() {
        if (!lastGood.isEmpty()) {
            return;
        }

        synchronized (refreshLock) {
            final Instant now = Instant.now();
            if (lastGood.isEmpty() && !now.isBefore(nextInitialLoadAttempt)) {
                log.info("method=ensureLoaded event=rail.upstream.peti operation=ensureLoaded outcome=refresh reason=empty_snapshot");
                nextInitialLoadAttempt = now.plus(INITIAL_LOAD_RETRY_DELAY);
                refreshLocked();
            }
        }

        if (lastGood.isEmpty()) {
            log.warn("method=ensureLoaded event=rail.upstream.peti operation=ensureLoaded outcome=empty "
                    + "detail=generating_without_stop_assignments");
        }
    }

    /**
     * Scheduled fetch: refresh the PETI snapshot once a day. Fetches the stops XML and atomically
     * swaps the snapshot on success. On any failure, keeps the last-good snapshot and records the
     * error, so a caller never has to handle an outage itself.
     */
    @Scheduled(cron = "${updater.netex.peti.cron:30 0 2 * * *}", zone = "UTC")
    public void refresh() {
        synchronized (refreshLock) {
            nextInitialLoadAttempt = Instant.now().plus(INITIAL_LOAD_RETRY_DELAY);
            refreshLocked();
        }
    }

    /**
     * Safety-net retry for a failed {@link #refresh()}: without this, a failed refresh would leave the
     * snapshot stale until the next explicit refresh call, since a failure keeps {@code lastGood} intact
     * (non-empty) and therefore never triggers {@link #ensureLoaded()}'s on-demand path either. Runs hourly
     * but is a no-op whenever the last attempt (explicit or on-demand) succeeded, so it only does work while
     * genuinely needed.
     */
    @Scheduled(cron = "${updater.netex.peti.retry-cron:0 0 * * * *}", zone = "UTC")
    public void retryIfLastFailed() {
        if (lastFetchResult == null || PetiFetchResult.OUTCOME_SUCCESS.equals(lastFetchResult.outcome())) {
            return;
        }
        synchronized (refreshLock) {
            if (lastFetchResult != null && PetiFetchResult.OUTCOME_ERROR.equals(lastFetchResult.outcome())) {
                log.info("method=retryIfLastFailed event=rail.upstream.peti operation=retryIfLastFailed "
                        + "outcome=refresh reason=previous_attempt_failed");
                refreshLocked();
            }
        }
    }

    private void refreshLocked() {
        final StopWatch stopWatch = StopWatch.createStarted();
        int httpStatus = 0;
        long bodySize = 0;

        try {
            final ResponseEntity<byte[]> entity = webClient.get()
                    .uri(petiUrl)
                    .retrieve()
                    .toEntity(byte[].class)
                    .timeout(requestTimeout)
                    .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                            .maxBackoff(RETRY_MAX_BACKOFF)
                            .jitter(0.0)
                            .filter(CachingPetiStopSource::isRetryableFailure)
                            .doBeforeRetry(signal -> log.warn(
                                    "method=refresh event=rail.upstream.peti operation=fetchPeti outcome=retry "
                                            + "attempt={} tookMs={} errorType={}",
                                    signal.totalRetries() + 1, stopWatch.getDuration().toMillis(),
                                    signal.failure().getClass().getSimpleName())))
                    .block(blockTimeout);

            httpStatus = entity != null ? entity.getStatusCode().value() : 0;
            final byte[] xmlBytes = entity != null ? entity.getBody() : null;

            if (xmlBytes == null || xmlBytes.length == 0) {
                throw new PetiParseException("Empty response body from PETI",
                        new IllegalStateException("null or empty body"));
            }
            bodySize = xmlBytes.length;

            final List<PetiStop> parsed = parseXmlBytes(xmlBytes);

            if (parsed.isEmpty()) {
                // A well-formed response that yields zero stops is still not usable data: applySnapshot()
                // deliberately ignores it and keeps the last-good snapshot, so recording this as success would
                // silently disable retryIfLastFailed()'s hourly safety-net until the next explicit refresh - as
                // if the empty result were as good as a real one. Route it through the same catch block as
                // any other parse failure instead, so it is recorded as an error and retried.
                throw new PetiParseException("Parsed PETI response contained zero stops",
                        new IllegalStateException("empty parsed result"));
            }

            final long durationMs = stopWatch.getDuration().toMillis();

            applySnapshot(parsed);

            final int quayCount = parsed.stream().mapToInt(s -> s.quays().size()).sum();
            lastFetchResult = PetiFetchResult.success(httpStatus, durationMs,
                    parsed.size(), quayCount, bodySize);

            log.info("method=refresh event=rail.upstream.peti operation=fetchPeti outcome=success httpStatus={} " +
                    "tookMs={} stopPlaces={} quays={} bodySize={}",
                    httpStatus, durationMs, parsed.size(), quayCount, bodySize);

        } catch (final Exception e) {
            final long durationMs = stopWatch.getDuration().toMillis();
            final Throwable unwrapped = unwrapRetryExhausted(e);
            if (unwrapped instanceof final WebClientResponseException responseException) {
                httpStatus = responseException.getStatusCode().value();
            }
            lastFetchResult = PetiFetchResult.error(httpStatus, durationMs, bodySize,
                    unwrapped.getClass().getSimpleName());
            // The old snapshot (if any) is kept as-is — applySnapshot() is only ever called on success — so log its
            // age here to make clear how stale the data generation is now running on.
            final boolean hasFallback = !lastGood.isEmpty();
            log.error("method=refresh event=rail.upstream.peti operation=fetchPeti outcome=error httpStatus={} " +
                    "tookMs={} errorType={} keepingOldSnapshot={} oldSnapshotAgeSeconds={}", httpStatus, durationMs,
                    unwrapped.getClass().getSimpleName(), hasFallback, hasFallback ? getSnapshotAgeSeconds() : -1, e);
        }
    }

    /** True for failures worth retrying: 5xx responses, connection errors, and per-attempt timeouts. */
    private static boolean isRetryableFailure(final Throwable throwable) {
        if (throwable instanceof final WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        if (throwable instanceof TimeoutException) {
            return true;
        }
        final Throwable cause = throwable.getCause();
        return throwable instanceof IOException
                || ((cause instanceof IOException || cause instanceof TimeoutException));
    }

    /**
     * When {@link Retry#backoff} exhausts its attempts, Reactor wraps the last failure in a
     * {@code RetryExhaustedException}. Unwrap it so logging/status extraction see the real cause,
     * exactly as if no retry had happened.
     */
    private static Throwable unwrapRetryExhausted(final Throwable throwable) {
        final Throwable unwrapped = Exceptions.unwrap(throwable);
        if (Exceptions.isRetryExhausted(unwrapped) && unwrapped.getCause() != null) {
            return Exceptions.unwrap(unwrapped.getCause());
        }
        return unwrapped;
    }

    /**
     * Parses a stops NeTEx XML response body. Pure function — does not mutate the cached snapshot;
     * callers swap results in via {@link #applySnapshot(List)}. Package-private seam for unit testing
     * without HTTP.
     *
     * @param xmlBytes raw response body
     * @return parsed list of PetiStop records
     * @throws PetiParseException if the body is not parseable NeTEx
     */
    List<PetiStop> parseXmlBytes(final byte[] xmlBytes) {
        return parser.parse(new ByteArrayInputStream(xmlBytes));
    }

    /**
     * Atomically swaps in a newly-parsed snapshot. Empty results are ignored so a failed
     * or empty fetch never clobbers the last-good data. Package-private seam so tests can
     * pre-load a snapshot without HTTP.
     */
    void applySnapshot(final List<PetiStop> parsed) {
        if (!parsed.isEmpty()) {
            lastGood = List.copyOf(parsed);
            lastSuccessfulFetch = Instant.now();
        }
    }

    /**
     * Returns the age of the current snapshot in seconds, or -1 if never loaded.
     */
    public long getSnapshotAgeSeconds() {
        final Instant snapshot = lastSuccessfulFetch;
        if (snapshot == null) {
            return -1L;
        }
        return Duration.between(snapshot, Instant.now()).toSeconds();
    }

    /**
     * Returns the result of the last fetch operation, or null if never attempted.
     */
    public PetiFetchResult getLastFetchResult() {
        return lastFetchResult;
    }

    /** Visible for testing. */
    String getPetiUrl() {
        return petiUrl;
    }
}
