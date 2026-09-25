package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import reactor.core.Exceptions;

/**
 * HTTP-backed PetiStopSource that fetches the PETI rail stops as NeTEx XML,
 * parses it with
 * PetiNeTExParser, and caches the result as a last-good snapshot.
 *
 * Refreshed by its consumers rather than on a schedule: NeTEx package
 * generation refreshes at the
 * start of a run, and {@link #ensureLoaded()} covers a cold JVM.
 *
 * On fetch/parse failure, the last-good snapshot is preserved — generation
 * continues with stale but valid data rather than empty/partial.
 */
@Component
@ConditionalOnProperty(name = "updater.netex.peti.enabled", havingValue = "true", matchIfMissing = true)
public class CachingPetiStopSource implements PetiStopSource {

    private static final Logger log = LoggerFactory.getLogger(CachingPetiStopSource.class);

    private final WebClient webClient;
    private final PetiNeTExParser parser;
    private final String petiUrl;
    private final Duration blockTimeout;

    private volatile List<PetiStop> lastGood = List.of();
    private volatile Instant lastSuccessfulFetch = null;
    private volatile PetiFetchResult lastFetchResult = null;

    public CachingPetiStopSource(
            final WebClient webClient,
            final PetiNeTExParser parser,
            final @Value("${updater.netex.peti.url}") String petiUrl,
            final @Value("${updater.netex.peti.block-timeout-seconds:30}") int blockTimeoutSeconds) {
        this.webClient = webClient;
        this.parser = parser;
        this.petiUrl = petiUrl;
        this.blockTimeout = Duration.ofSeconds(blockTimeoutSeconds);
    }

    @Override
    public List<PetiStop> getStops() {
        return lastGood;
    }

    /**
     * Loads the snapshot on demand when empty, so generation never depends on the
     * daily
     * warm-up having run in this JVM (e.g. after a restart or an early manual run).
     * When the
     * feed is unavailable, generation degrades to a package without stop
     * assignments rather
     * than failing outright.
     */
    @Override
    public void ensureLoaded() {
        if (lastGood.isEmpty()) {
            log.info("rail.upstream.peti operation=ensureLoaded outcome=refresh reason=empty_snapshot");
            refresh();
        }
        if (lastGood.isEmpty()) {
            log.warn("rail.upstream.peti operation=ensureLoaded outcome=empty "
                    + "detail=generating_without_stop_assignments");
        }
    }

    /**
     * Fetches the stops XML and atomically swaps the snapshot on success. On any
     * failure, keeps the
     * last-good snapshot and records the error, so a caller never has to handle an
     * outage itself.
     */
    @Override
    public void refresh() {
        final long startNanos = System.nanoTime();
        int httpStatus = 0;
        long bodySize = 0;

        try {
            final ResponseEntity<byte[]> entity = webClient.get()
                    .uri(petiUrl)
                    .retrieve()
                    .toEntity(byte[].class)
                    .block(blockTimeout);

            httpStatus = entity != null ? entity.getStatusCode().value() : 0;
            final byte[] xmlBytes = entity != null ? entity.getBody() : null;

            if (xmlBytes == null || xmlBytes.length == 0) {
                throw new PetiParseException("Empty response body from PETI",
                        new IllegalStateException("null or empty body"));
            }
            bodySize = xmlBytes.length;

            final List<PetiStop> parsed = parseXmlBytes(xmlBytes);
            final long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

            applySnapshot(parsed);

            final int quayCount = parsed.stream().mapToInt(s -> s.quays().size()).sum();
            lastFetchResult = PetiFetchResult.success(httpStatus, durationMs,
                    parsed.size(), quayCount, bodySize);

            log.info("rail.upstream.peti operation=fetchPeti outcome=success http_status={} " +
                    "duration_ms={} stop_places={} quays={} body_size={}",
                    httpStatus, durationMs, parsed.size(), quayCount, bodySize);

        } catch (final WebClientResponseException e) {
            httpStatus = e.getStatusCode().value();
            final long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            lastFetchResult = PetiFetchResult.error(httpStatus, durationMs, bodySize,
                    e.getClass().getSimpleName());
            log.error("rail.upstream.peti operation=fetchPeti outcome=error http_status={} " +
                    "duration_ms={} error.type={}", httpStatus, durationMs, e.getClass().getSimpleName(), e);

        } catch (final Exception e) {
            final long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            final Throwable unwrapped = Exceptions.unwrap(e);
            lastFetchResult = PetiFetchResult.error(httpStatus, durationMs, bodySize,
                    unwrapped.getClass().getSimpleName());
            log.error("rail.upstream.peti operation=fetchPeti outcome=error http_status={} " +
                    "duration_ms={} error.type={}", httpStatus, durationMs,
                    unwrapped.getClass().getSimpleName(), e);
        }
    }

    /**
     * Parses a stops NeTEx XML response body. Pure function — does not mutate the
     * cached snapshot;
     * callers swap results in via {@link #applySnapshot(List)}. Package-private
     * seam for unit testing
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
     * Atomically swaps in a newly-parsed snapshot. Empty results are ignored so a
     * failed
     * or empty fetch never clobbers the last-good data. Package-private seam so
     * tests can
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
