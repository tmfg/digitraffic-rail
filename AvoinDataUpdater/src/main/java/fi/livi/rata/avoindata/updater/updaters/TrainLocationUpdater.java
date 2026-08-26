package fi.livi.rata.avoindata.updater.updaters;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.deserializers.PalaDeserializationResult;
import fi.livi.rata.avoindata.updater.deserializers.PalaYksikkoDeserializer;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenTrainLocationFilter;
import fi.livi.rata.avoindata.updater.service.trainlocation.TrainLocationNearTrackFilterService;

import static fi.livi.rata.avoindata.updater.updaters.UpdateLogger.logUpdate;

@Service
public class TrainLocationUpdater {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Autowired
    private RecentlySeenTrainLocationFilter recentlySeenTrainLocationFilter;

    @Autowired
    private TrainLocationNearTrackFilterService trainLocationNearTrackFilterService;

    @Autowired
    private RipaService ripaService;

    @Autowired
    private PalaYksikkoDeserializer palaYksikkoDeserializer;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private LastUpdateService lastUpdateService;

    private static final DecimalFormat IP_LOCATION_FILTER_PRECISION =
            new DecimalFormat("#.000000", DecimalFormatSymbols.getInstance(Locale.ROOT));
    // propertyName excludes the top-level fields we never read to shrink the payload.
    private static final String PALA_YKSIKOT_PATH = "0.2/yksikot.json?propertyName=-epavarmuus,-lahdeKulkutiedot,-suunta";

    // Adaptive back-off when PALA is unavailable (5xx or network failure): skip cycles for an exponentially growing
    // window so we stop hammering PALA while it recovers. Reset on the first successful fetch.
    private static final long BACKOFF_BASE_MS = 1000;
    private static final long BACKOFF_MAX_MS = 30000;

    private static final Duration MQTT_PUBLISH_OBSERVE_TIMEOUT = Duration.ofSeconds(2);

    private long backoffUntilMs = 0;
    private int consecutiveUpstreamFailures = 0;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public synchronized void trainLocation() {
        if (isInUpstreamBackoff()) {
            log.debug("operation=ingestTrainLocations outcome=skipped_backoff rail.upstream.pala.backoff_remaining_ms={}",
                    backoffUntilMs - System.currentTimeMillis());
            return;
        }

        final long startMs = System.currentTimeMillis();
        final IngestionMetrics metrics = new IngestionMetrics();

        try {
            final ZonedDateTime start = DateProvider.nowInHelsinki();
            final String responseBody = fetchFromPala(metrics);
            final List<TrainLocation> trainLocations = deserialize(responseBody, metrics);
            recordStaleness(trainLocations, metrics);
            final ZonedDateTime middle = DateProvider.nowInHelsinki();
            countPositionTypes(trainLocations, metrics);

            final List<TrainLocation> filteredTrainLocations = filterTrains(trainLocations, metrics);

            publishToMqtt(filteredTrainLocations, metrics);
            trainLocationRepository.persist(filteredTrainLocations);
            metrics.recordsPersisted = filteredTrainLocations.size();

            final ZonedDateTime end = DateProvider.nowInHelsinki();
            logUpdate(end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli(), "train-location", filteredTrainLocations.size(), middle.toInstant().toEpochMilli() - start.toInstant().toEpochMilli());

            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS);
            resetUpstreamBackoff();
        } catch (final Exception e) {
            metrics.markError(e);
            // Companion error log carries the full message + stack trace (and the offending unit for deser errors);
            // the wide-event line below carries the structured metrics.
            log.error("Error updating train locations from PALA rail.error.train_number={} offending unit: {}",
                    metrics.errorTrainNumber, metrics.errorSampleJson, e);
            if (isUpstreamFailure(metrics)) {
                registerUpstreamFailure(metrics);
            }
        } finally {
            metrics.durationMs = System.currentTimeMillis() - startMs;
            // Surface data-quality deser errors that did NOT fail the cycle (the sample JSON has spaces, so it cannot
            // live in the space-delimited wide-event line — it goes in this companion log instead).
            if (metrics.isSuccess() && metrics.deserializationErrors > 0) {
                log.warn("Some PALA units failed to deserialize rail.train_location.deserialization.errors={} "
                                + "rail.error.train_number={} offending unit: {}",
                        metrics.deserializationErrors, metrics.errorTrainNumber, metrics.errorSampleJson);
            }
            logIngestionCycle(metrics);
        }
    }

    /**
     * Fetches the raw PALA response body and records the upstream HTTP metrics. Latency is captured on both the success
     * and error paths; the HTTP status is the real response code on an HTTP error and {@code 0} on a network/transport
     * failure, so the PALA availability SLI can distinguish 5xx/4xx from connection failures.
     */
    private String fetchFromPala(final IngestionMetrics metrics) {
        final long httpStartMs = System.currentTimeMillis();
        try {
            final String responseBody = ripaService.getFromPalaAsString(PALA_YKSIKOT_PATH);
            metrics.httpStatus = 200;
            metrics.responseSizeBytes = responseBody != null ? responseBody.length() : 0;
            if (responseBody == null) {
                throw new IllegalStateException("PALA returned an empty response body");
            }
            return responseBody;
        } catch (final WebClientResponseException e) {
            metrics.httpStatus = e.getStatusCode().value();
            throw e;
        } finally {
            metrics.httpLatencyMs = System.currentTimeMillis() - httpStartMs;
        }
    }

    /**
     * Parses the PALA response into entities and records the per-cycle deserialization stats. Per-unit failures are
     * counted (and captured) but never abort the batch — only a whole-body parse failure propagates and fails the cycle.
     */
    private List<TrainLocation> deserialize(final String responseBody, final IngestionMetrics metrics) {
        final PalaDeserializationResult result = palaYksikkoDeserializer.deserializeWithStats(responseBody);
        metrics.recordsReceived = result.receivedCount();
        metrics.droppedNoCoordinate = result.droppedNoCoordinate();
        metrics.droppedNoSpeed = result.droppedNoSpeed();
        metrics.deserializationErrors = result.deserializationErrors();
        metrics.errorTrainNumber = result.firstErrorTrainNumber();
        metrics.errorSampleJson = result.firstErrorSampleJson();
        return result.locations();
    }

    /** Records the newest PALA source timestamp and the resulting ingestion staleness for the wide-event log. */
    private static void recordStaleness(final List<TrainLocation> trainLocations, final IngestionMetrics metrics) {
        metrics.ingestionTime = DateProvider.nowInHelsinki();
        metrics.sourceTime = trainLocations.stream()
                .map(tl -> tl.trainLocationId.timestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (metrics.sourceTime != null) {
            metrics.stalenessMs = Duration.between(metrics.sourceTime.toInstant(), metrics.ingestionTime.toInstant())
                    .toMillis();
        }
    }

    private boolean isUpstreamFailure(final IngestionMetrics metrics) {
        // 0 = network/transport failure (fetch never reached a 200); >=500 = PALA server error. 4xx is our fault, so we
        // do not back off for it.
        return metrics.httpStatus == 0 || metrics.httpStatus >= 500;
    }

    private boolean isInUpstreamBackoff() {
        return System.currentTimeMillis() < backoffUntilMs;
    }

    private void registerUpstreamFailure(final IngestionMetrics metrics) {
        consecutiveUpstreamFailures++;
        final long wait = Math.min(BACKOFF_BASE_MS << Math.min(consecutiveUpstreamFailures - 1, 5), BACKOFF_MAX_MS);
        backoffUntilMs = System.currentTimeMillis() + wait;
        metrics.backoffActive = true;
        metrics.backoffMs = wait;
    }

    private void resetUpstreamBackoff() {
        consecutiveUpstreamFailures = 0;
        backoffUntilMs = 0;
    }

    private static void countPositionTypes(final List<TrainLocation> trainLocations, final IngestionMetrics metrics) {
        for (final TrainLocation trainLocation : trainLocations) {
            if (trainLocation.isGpsLocation) {
                metrics.positionsGps++;
            } else {
                metrics.positionsCalculated++;
            }
        }
    }

    /**
     * Runs the three train-location filters (recently-seen dedup, IP-fallback, near-track) and records the per-filter
     * drop counts into {@code metrics}.
     */
    private List<TrainLocation> filterTrains(final List<TrainLocation> trainLocations, final IngestionMetrics metrics) {
        final List<TrainLocation> recentlySeenFiltered = recentlySeenTrainLocationFilter.filter(trainLocations);
        metrics.droppedRecentlySeen = trainLocations.size() - recentlySeenFiltered.size();

        final List<TrainLocation> afterIpFilter = new ArrayList<>();
        for (final TrainLocation t : recentlySeenFiltered) {
            if (isIpFallbackLocation(t.location)) {
                metrics.droppedIpFallback++;
                log.info("Found IP location for {} ({} / {})", t, t.location, t.locationEpsg3067);
            } else {
                afterIpFilter.add(t);
            }
        }

        final List<TrainLocation> result = new ArrayList<>();
        for (final TrainLocation t : afterIpFilter) {
            if (trainLocationNearTrackFilterService.isTrainLocationNearTrack(t)) {
                result.add(t);
            } else {
                metrics.droppedOffTrack++;
                log.debug("operation=filterTrainLocation rail.entity.type=train_location rail.filter.reason=off_track "
                                + "rail.train.number={} rail.train.departure_date={}",
                        t.trainLocationId.trainNumber, t.trainLocationId.departureDate);
            }
        }

        metrics.recordsProcessed = result.size();
        return result;
    }

    private void publishToMqtt(final List<TrainLocation> trainLocations, final IngestionMetrics metrics) {
        if (!mqttPublishService.isEnabled()) {
            // MQTT disabled (e.g. in tests) — nothing is published, so this is not a successful publish.
            metrics.mqttSuccess = false;
            return;
        }
        final long mqttStartMs = System.currentTimeMillis();
        try {
            final List<Future<Message<String>>> futures = mqttPublishService.publish(
                    s -> String.format("train-locations/%s/%s", s.trainLocationId.departureDate, s.trainLocationId.trainNumber),
                    trainLocations, null);
            metrics.mqttSuccess = allPublished(futures);
        } catch (final Exception e) {
            log.error("MQTT update failed.", e);
            metrics.mqttSuccess = false;
        }
        metrics.mqttLatencyMs = System.currentTimeMillis() - mqttStartMs;
    }

    /**
     * Waits (bounded by {@link #MQTT_PUBLISH_OBSERVE_TIMEOUT}) for every publish future and reports whether all were
     * delivered. A {@code null} future means serialization failed; a {@code null} result means the async send failed;
     * a timeout means delivery is unconfirmed — all count as not-published so the metric never claims a success it
     * cannot observe. An empty batch is trivially successful.
     */
    private static boolean allPublished(final List<Future<Message<String>>> futures) {
        final long deadlineNs = System.nanoTime() + MQTT_PUBLISH_OBSERVE_TIMEOUT.toNanos();
        for (final Future<Message<String>> future : futures) {
            if (future == null) {
                return false;
            }
            final long remainingNs = deadlineNs - System.nanoTime();
            if (remainingNs <= 0) {
                return false;
            }
            try {
                if (future.get(remainingNs, TimeUnit.NANOSECONDS) == null) {
                    return false;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (final ExecutionException | TimeoutException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Emits the wide-event log line summarising one ingestion cycle. Success and error cycles emit the <b>same</b>
     * field set (zeros/NULL where a value is unavailable) so log-based aggregations never have to cope with missing
     * fields; only the log level differs. The full error message + stack trace live in the companion {@code log.error}
     * in {@link #trainLocation()}.
     */
    private void logIngestionCycle(final IngestionMetrics m) {
        final String message = "operation=ingestTrainLocations outcome={} duration_ms={} "
                + "rail.source.system=RIPA rail.source.api=pala-api rail.source.endpoint=/0.2/yksikot.json "
                + "rail.entity.type=train_location "
                + "error.type={} rail.error.train_number={} "
                + "rail.train_location.records.received={} rail.train_location.records.processed={} "
                + "rail.train_location.records.persisted={} rail.train_location.deserialization.errors={} "
                + "rail.train_location.records.dropped.no_coordinate={} "
                + "rail.train_location.records.dropped.no_speed={} "
                + "rail.train_location.records.dropped.recently_seen={} "
                + "rail.train_location.records.dropped.ip_fallback={} "
                + "rail.train_location.records.dropped.off_track={} "
                + "rail.train_location.records.dropped.total={} "
                + "rail.train_location.positions.gps={} rail.train_location.positions.calculated={} "
                + "rail.train_location.positions.calculated_ratio={} "
                + "rail.train_location.source_time={} rail.train_location.ingestion_time={} "
                + "rail.train_location.staleness_ms={} "
                + "rail.upstream.pala.http_status={} rail.upstream.pala.response_size_bytes={} "
                + "rail.upstream.pala.latency_ms={} "
                + "rail.mqtt.publish_success={} rail.mqtt.publish_latency_ms={} "
                + "rail.upstream.pala.backoff_active={} rail.upstream.pala.backoff_ms={}";
        final Object[] args = {
                m.outcome, m.durationMs,
                nullSafe(m.errorType), nullSafe(m.errorTrainNumber),
                m.recordsReceived, m.recordsProcessed,
                m.recordsPersisted, m.deserializationErrors,
                m.droppedNoCoordinate,
                m.droppedNoSpeed,
                m.droppedRecentlySeen,
                m.droppedIpFallback,
                m.droppedOffTrack,
                m.droppedTotal(),
                m.positionsGps, m.positionsCalculated,
                String.format(Locale.ROOT, "%.2f", m.calculatedRatio()),
                m.sourceTime != null ? m.sourceTime.toInstant() : "NULL",
                m.ingestionTime != null ? m.ingestionTime.toInstant() : "NULL",
                m.stalenessMs,
                m.httpStatus, m.responseSizeBytes,
                m.httpLatencyMs,
                m.mqttSuccess, m.mqttLatencyMs,
                m.backoffActive, m.backoffMs
        };
        if (m.isSuccess()) {
            log.info(message, args);
        } else {
            log.error(message, args);
        }
    }

    /** {@code "NULL"} is converted to a JSON null by the key-value log provider. */
    private static String nullSafe(final String value) {
        return value != null ? value : "NULL";
    }

    /**
     * Detects the IP-based geolocation fallback coordinate (Helsinki default). When a GPS device fails it can fall back
     * to IP-based geolocation, returning Helsinki default coordinates. Extracted from the {@link #filterTrains} lambda
     * so it can be tested against the real production logic. The formatter is pinned to {@link Locale#ROOT} so the
     * dot-separated comparison is locale-independent.
     */
    static boolean isIpFallbackLocation(final Point wgs84Location) {
        final String yLocation = IP_LOCATION_FILTER_PRECISION.format(wgs84Location.getY());
        final String xLocation = IP_LOCATION_FILTER_PRECISION.format(wgs84Location.getX());
        return (yLocation.equals("60.170799") || yLocation.equals("60.170800")) && xLocation.equals("24.937500");
    }

    /**
     * Mutable accumulator for the fields of the per-cycle wide-event log. Grouping them here keeps
     * {@link #trainLocation()} readable and lets each ingestion step be reasoned about in isolation.
     */
    private static final class IngestionMetrics {
        private long durationMs;
        private String outcome = "success";
        private String errorType;
        private String errorTrainNumber;
        private String errorSampleJson;

        private int recordsReceived;
        private int recordsProcessed;
        private int recordsPersisted;
        private int deserializationErrors;

        private int droppedNoCoordinate;
        private int droppedNoSpeed;
        private int droppedRecentlySeen;
        private int droppedIpFallback;
        private int droppedOffTrack;

        private int positionsGps;
        private int positionsCalculated;

        private ZonedDateTime sourceTime;
        private ZonedDateTime ingestionTime;
        private long stalenessMs;

        private int httpStatus;
        private long httpLatencyMs;
        private int responseSizeBytes;

        private boolean mqttSuccess;
        private long mqttLatencyMs;

        private boolean backoffActive;
        private long backoffMs;

        private boolean isSuccess() {
            return "success".equals(outcome);
        }

        private int droppedTotal() {
            return droppedNoCoordinate + droppedNoSpeed + deserializationErrors + droppedRecentlySeen + droppedIpFallback + droppedOffTrack;
        }

        private double calculatedRatio() {
            return recordsReceived > 0 ? (double) positionsCalculated / recordsReceived : 0.0;
        }

        /** Records the first error of the cycle; a later failure in the finally-path will not overwrite it. */
        private void markError(final Exception e) {
            if (isSuccess()) {
                outcome = "error";
                errorType = e.getClass().getSimpleName();
            }
        }
    }
}
