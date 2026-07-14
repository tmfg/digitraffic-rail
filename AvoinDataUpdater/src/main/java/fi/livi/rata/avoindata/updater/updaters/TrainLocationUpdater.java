package fi.livi.rata.avoindata.updater.updaters;

import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.deserializers.PalaDeserializationException;
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

    private static final DecimalFormat IP_LOCATION_FILTER_PRECISION = new DecimalFormat("#.000000");
    private static final String PALA_YKSIKOT_PATH = "0.2/yksikot.json";

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public synchronized void trainLocation() {
        final long startMs = System.currentTimeMillis();
        final IngestionMetrics metrics = new IngestionMetrics();

        try {
            final ZonedDateTime start = DateProvider.nowInHelsinki();
            final String responseBody = fetchFromPala(metrics);
            final List<TrainLocation> trainLocations = deserialize(responseBody, metrics);
            final ZonedDateTime middle = DateProvider.nowInHelsinki();
            countPositionTypes(trainLocations, metrics);

            final List<TrainLocation> filteredTrainLocations = filterTrains(trainLocations, metrics);

            publishToMqtt(filteredTrainLocations, metrics);
            trainLocationRepository.persist(filteredTrainLocations);

            final ZonedDateTime end = DateProvider.nowInHelsinki();
            logUpdate(end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli(), "train-location", filteredTrainLocations.size(), middle.toInstant().toEpochMilli() - start.toInstant().toEpochMilli());

            metrics.recordsPersisted = filteredTrainLocations.size();
            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS);
        } catch (final Exception e) {
            metrics.markError(e);
            // Companion error log carries the full message + stack trace (and the offending unit for deser errors);
            // the wide-event line below carries the structured metrics.
            log.error("Error updating train locations from PALA rail.error.train_number={} offending unit: {}",
                    metrics.errorTrainNumber, metrics.errorSampleJson, e);
        } finally {
            metrics.durationMs = System.currentTimeMillis() - startMs;
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
            metrics.responseSizeBytes = responseBody != null ? responseBody.getBytes().length : 0;
            return responseBody;
        } catch (final WebClientResponseException e) {
            metrics.httpStatus = e.getStatusCode().value();
            throw e;
        } finally {
            metrics.httpLatencyMs = System.currentTimeMillis() - httpStartMs;
        }
    }

    /** Parses the PALA response into entities; on failure it flags a deserialization error (with the offending train
     * number + JSON snippet when available) and rethrows. */
    private List<TrainLocation> deserialize(final String responseBody, final IngestionMetrics metrics) {
        try {
            final List<TrainLocation> trainLocations = palaYksikkoDeserializer.deserialize(responseBody);
            metrics.recordsReceived = trainLocations.size();
            return trainLocations;
        } catch (final PalaDeserializationException e) {
            metrics.deserializationErrors = 1;
            metrics.errorTrainNumber = e.getTrainNumber();
            metrics.errorSampleJson = e.getSampleJson();
            metrics.markError(e);
            throw e;
        } catch (final Exception e) {
            metrics.deserializationErrors = 1;
            metrics.markError(e);
            throw e;
        }
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
                log.info("Found IP location for {} ({} / {})", t, t.location, t.liikeLocation);
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

    /** Publishes the filtered locations to MQTT; a failure is logged but does not abort the DB persist. */
    private void publishToMqtt(final List<TrainLocation> trainLocations, final IngestionMetrics metrics) {
        final long mqttStartMs = System.currentTimeMillis();
        try {
            mqttPublishService.publish(
                    s -> String.format("train-locations/%s/%s", s.trainLocationId.departureDate, s.trainLocationId.trainNumber),
                    trainLocations, null);
            metrics.mqttSuccess = true;
        } catch (final Exception e) {
            log.error("MQTT updated failed. Still trying to update database.", e);
            metrics.mqttSuccess = false;
        }
        metrics.mqttLatencyMs = System.currentTimeMillis() - mqttStartMs;
    }

    /** Emits the wide-event log line summarising one ingestion cycle. */
    private void logIngestionCycle(final IngestionMetrics m) {
        if (m.isSuccess()) {
            log.info("operation=ingestTrainLocations outcome={} duration_ms={} "
                            + "rail.source.system=RIPA rail.source.api=pala-api rail.source.endpoint=/0.2/yksikot.json "
                            + "rail.entity.type=train_location "
                            + "rail.train_location.records.received={} rail.train_location.records.processed={} "
                            + "rail.train_location.records.persisted={} rail.train_location.deserialization.errors={} "
                            + "rail.train_location.records.dropped.recently_seen={} "
                            + "rail.train_location.records.dropped.ip_fallback={} "
                            + "rail.train_location.records.dropped.off_track={} "
                            + "rail.train_location.records.dropped.total={} "
                            + "rail.train_location.positions.gps={} rail.train_location.positions.calculated={} "
                            + "rail.train_location.positions.calculated_ratio={} "
                            + "rail.upstream.pala.http_status={} rail.upstream.pala.response_size_bytes={} "
                            + "rail.upstream.pala.latency_ms={} "
                            + "rail.mqtt.publish_success={} rail.mqtt.publish_latency_ms={}",
                    m.outcome, m.durationMs,
                    m.recordsReceived, m.recordsProcessed,
                    m.recordsPersisted, m.deserializationErrors,
                    m.droppedRecentlySeen,
                    m.droppedIpFallback,
                    m.droppedOffTrack,
                    m.droppedTotal(),
                    m.positionsGps, m.positionsCalculated,
                    String.format(Locale.ROOT, "%.2f", m.calculatedRatio()),
                    m.httpStatus, m.responseSizeBytes,
                    m.httpLatencyMs,
                    m.mqttSuccess, m.mqttLatencyMs);
        } else {
            // error.message and stack trace are carried by the companion log.error in trainLocation(); this structured
            // line keeps only space-safe scalar fields (the key=value log provider is space-delimited).
            log.error("operation=ingestTrainLocations outcome={} duration_ms={} "
                            + "rail.source.system=RIPA rail.source.api=pala-api rail.source.endpoint=/0.2/yksikot.json "
                            + "rail.entity.type=train_location "
                            + "error.type={} rail.error.train_number={} "
                            + "rail.train_location.records.received={} rail.train_location.records.processed={} "
                            + "rail.train_location.deserialization.errors={} "
                            + "rail.upstream.pala.http_status={} rail.upstream.pala.latency_ms={}",
                    m.outcome, m.durationMs,
                    m.errorType, m.errorTrainNumber,
                    m.recordsReceived, m.recordsProcessed,
                    m.deserializationErrors,
                    m.httpStatus, m.httpLatencyMs);
        }
    }

    /**
     * Detects the IP-based geolocation fallback coordinate (Helsinki default). When a GPS device fails it can fall back
     * to IP-based geolocation, returning Helsinki default coordinates. Extracted from the {@link #filterTrains} lambda
     * so it can be tested against the real production logic.
     *
     * <p>Note: {@link #IP_LOCATION_FILTER_PRECISION} uses the default JVM locale and the comparison strings use a comma
     * decimal separator, so this only matches under a comma-locale JVM (e.g. fi_FI). This locale sensitivity is a known
     * finding to revisit during the PALA migration.
     */
    static boolean isIpFallbackLocation(final Point wgs84Location) {
        final String yLocation = IP_LOCATION_FILTER_PRECISION.format(wgs84Location.getY());
        final String xLocation = IP_LOCATION_FILTER_PRECISION.format(wgs84Location.getX());
        return (yLocation.equals("60,170799") || yLocation.equals("60,170800")) && xLocation.equals("24,937500");
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

        private int droppedRecentlySeen;
        private int droppedIpFallback;
        private int droppedOffTrack;

        private int positionsGps;
        private int positionsCalculated;

        private int httpStatus;
        private long httpLatencyMs;
        private int responseSizeBytes;

        private boolean mqttSuccess;
        private long mqttLatencyMs;

        private boolean isSuccess() {
            return "success".equals(outcome);
        }

        private int droppedTotal() {
            return droppedRecentlySeen + droppedIpFallback + droppedOffTrack;
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
