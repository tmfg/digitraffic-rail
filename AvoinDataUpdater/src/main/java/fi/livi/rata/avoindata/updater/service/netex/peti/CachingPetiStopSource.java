package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

/**
 * HTTP-backed PetiStopSource that fetches the Kooste PETI-rail-NeTEx.zip,
 * extracts stops.xml, parses it with PetiNeTExParser, and caches the result
 * as a last-good snapshot. Refreshes on schedule (03:30 UTC).
 *
 * <p>On fetch/parse failure, the last-good snapshot is preserved — generation
 * continues with stale but valid data rather than empty/partial.
 */
@Component
@ConditionalOnProperty(name = "updater.netex.peti.enabled", havingValue = "true", matchIfMissing = true)
public class CachingPetiStopSource implements PetiStopSource {

    private static final Logger log = LoggerFactory.getLogger(CachingPetiStopSource.class);
    private static final String STOPS_XML_ENTRY = "stops.xml";

    /** Upper bound on decompressed stops.xml size — defence-in-depth against zip bombs (~170× real data). */
    private static final long MAX_DECOMPRESSED_BYTES = 50L * 1024 * 1024;

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
     * Scheduled warm-up: refresh the PETI snapshot ahead of NeTEx generation.
     * Fetches the zip via HTTP, parses stops.xml, and atomically swaps the snapshot
     * on success. On any failure, keeps the last-good snapshot and records the error.
     */
    @Scheduled(cron = "${updater.netex.peti.cron:0 30 3 * * *}", zone = "UTC")
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
            final byte[] zipBytes = entity != null ? entity.getBody() : null;
            bodySize = zipBytes != null ? zipBytes.length : 0;

            if (zipBytes == null || zipBytes.length == 0) {
                throw new PetiParseException("Empty response body from PETI",
                        new IllegalStateException("null or empty body"));
            }

            final List<PetiStop> parsed = parseZipBytes(zipBytes);
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
     * Parse a zip byte array: extract stops.xml, guard its decompressed size, and parse
     * with PetiNeTExParser. Pure function — does not mutate the cached snapshot; callers
     * swap results in via {@link #applySnapshot(List)}. Package-private seam for unit
     * testing without HTTP.
     *
     * @param zipBytes raw zip file content
     * @return parsed list of PetiStop records
     * @throws PetiParseException if stops.xml is missing, unparseable, or exceeds the size cap
     */
    List<PetiStop> parseZipBytes(final byte[] zipBytes) {
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (STOPS_XML_ENTRY.equals(entry.getName())) {
                    final byte[] xmlBytes = readWithSizeCap(zis);
                    return parser.parse(new ByteArrayInputStream(xmlBytes));
                }
            }
        } catch (final PetiParseException e) {
            throw e;
        } catch (final IOException e) {
            throw new PetiParseException("Failed to read zip content", e);
        }
        throw new PetiParseException("stops.xml entry not found in zip",
                new IllegalStateException("no stops.xml entry"));
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
     * Reads a zip entry fully into memory, aborting if the decompressed size exceeds
     * {@link #MAX_DECOMPRESSED_BYTES} — defence-in-depth against zip bombs.
     */
    private static byte[] readWithSizeCap(final InputStream in) throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        final byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > MAX_DECOMPRESSED_BYTES) {
                throw new PetiParseException(
                        "Decompressed stops.xml exceeds size cap of " + MAX_DECOMPRESSED_BYTES + " bytes",
                        new IllegalStateException("decompressed size cap exceeded"));
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
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
