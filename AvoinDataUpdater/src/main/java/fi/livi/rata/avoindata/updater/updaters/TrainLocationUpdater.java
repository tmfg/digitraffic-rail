package fi.livi.rata.avoindata.updater.updaters;

import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.base.Strings;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.deserializers.PalaYksikkoDeserializer;
import fi.livi.rata.avoindata.updater.service.MQTTPublishService;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.recentlyseen.RecentlySeenTrainLocationFilter;
import fi.livi.rata.avoindata.updater.service.trainlocation.TrainLocationNearTrackFilterService;

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

    @Value("${updater.liikeinterface-url}")
    private String liikeinterfaceUrl;

    @Value("${updater.kupla-enabled:false}")
    private boolean isKuplaEnabled;

    @Value("${updater.pala-enabled:true}")
    private boolean isPalaEnabled;

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private LastUpdateService lastUpdateService;

    private static final DecimalFormat IP_LOCATION_FILTER_PRECISION = new DecimalFormat("#.000000");
    private static final String PALA_YKSIKOT_PATH = "0.2/yksikot.json";

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public synchronized void trainLocation() {
        if (isPalaEnabled) {
            trainLocationFromPala();
        } else if (isKuplaEnabled && !Strings.isNullOrEmpty(liikeinterfaceUrl)) {
            trainLocationFromKupla();
        }
    }

    private void trainLocationFromPala() {
        final long startMs = System.currentTimeMillis();
        int recordsReceived = 0;
        int recordsProcessed = 0;
        int droppedRecentlySeen = 0;
        int droppedIpFallback = 0;
        int droppedOffTrack = 0;
        int positionsGps = 0;
        int positionsCalculated = 0;
        int deserializationErrors = 0;
        boolean mqttSuccess = false;
        long mqttLatencyMs = 0;
        int httpStatus = 0;
        long httpLatencyMs = 0;
        int responseSizeBytes = 0;
        String outcome = "success";
        String errorType = null;
        String errorMessage = null;

        try {
            // Fetch from PALA
            final long httpStartMs = System.currentTimeMillis();
            final String responseBody = ripaService.getFromPalaAsString(PALA_YKSIKOT_PATH);
            httpLatencyMs = System.currentTimeMillis() - httpStartMs;
            httpStatus = 200;
            responseSizeBytes = responseBody != null ? responseBody.getBytes().length : 0;

            // Deserialize
            final List<TrainLocation> trainLocations;
            try {
                trainLocations = palaYksikkoDeserializer.deserialize(responseBody);
            } catch (final Exception e) {
                deserializationErrors = 1;
                outcome = "error";
                errorType = e.getClass().getSimpleName();
                errorMessage = e.getMessage();
                throw e;
            }
            recordsReceived = trainLocations.size();

            // Count GPS vs calculated positions
            for (final TrainLocation tl : trainLocations) {
                if (tl.isGpsLocation) {
                    positionsGps++;
                } else {
                    positionsCalculated++;
                }
            }

            // Filter
            final FilterResult filterResult = filterTrains(trainLocations);
            final List<TrainLocation> filteredTrainLocations = filterResult.result;
            droppedRecentlySeen = filterResult.droppedRecentlySeen;
            droppedIpFallback = filterResult.droppedIpFallback;
            droppedOffTrack = filterResult.droppedOffTrack;
            recordsProcessed = filteredTrainLocations.size();

            // MQTT publish
            final long mqttStartMs = System.currentTimeMillis();
            try {
                mqttPublishService.publish(
                        s -> String.format("train-locations/%s/%s", s.trainLocationId.departureDate, s.trainLocationId.trainNumber),
                        filteredTrainLocations, null);
                mqttSuccess = true;
            } catch (final Exception e) {
                log.error("MQTT updated failed. Still trying to update database.", e);
                mqttSuccess = false;
            }
            mqttLatencyMs = System.currentTimeMillis() - mqttStartMs;

            // Persist to DB
            trainLocationRepository.persist(filteredTrainLocations);

            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS);
        } catch (final Exception e) {
            if (outcome.equals("success")) {
                outcome = "error";
                errorType = e.getClass().getSimpleName();
                errorMessage = e.getMessage();
            }
            log.error("Error updating train locations from PALA", e);
        } finally {
            final long durationMs = System.currentTimeMillis() - startMs;
            final int droppedTotal = droppedRecentlySeen + droppedIpFallback + droppedOffTrack;
            final double calculatedRatio = recordsReceived > 0
                    ? (double) positionsCalculated / recordsReceived : 0.0;

            // Wide event log — one structured JSON line per ingestion cycle
            if (outcome.equals("success")) {
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
                        outcome, durationMs,
                        recordsReceived, recordsProcessed,
                        recordsProcessed, deserializationErrors,
                        droppedRecentlySeen,
                        droppedIpFallback,
                        droppedOffTrack,
                        droppedTotal,
                        positionsGps, positionsCalculated,
                        String.format("%.2f", calculatedRatio),
                        httpStatus, responseSizeBytes,
                        httpLatencyMs,
                        mqttSuccess, mqttLatencyMs);
            } else {
                log.error("operation=ingestTrainLocations outcome={} duration_ms={} "
                                + "rail.source.system=RIPA rail.source.api=pala-api rail.source.endpoint=/0.2/yksikot.json "
                                + "rail.entity.type=train_location "
                                + "error.type={} error.message=\"{}\" "
                                + "rail.train_location.records.received={} rail.train_location.records.processed={} "
                                + "rail.train_location.deserialization.errors={} "
                                + "rail.upstream.pala.http_status={} rail.upstream.pala.latency_ms={}",
                        outcome, durationMs,
                        errorType, errorMessage,
                        recordsReceived, recordsProcessed,
                        deserializationErrors,
                        httpStatus, httpLatencyMs);
            }
        }
    }

    /** Legacy KUPLA ingestion path — kept for rollback during migration. */
    private void trainLocationFromKupla() {
        try {
            final ZonedDateTime start = DateProvider.nowInHelsinki();
            final List<TrainLocation> trainLocations = Arrays.asList(ripaService.getFromRipa("kuplas", TrainLocation[].class));
            final List<TrainLocation> filteredTrainLocations = filterTrains(trainLocations).result;

            try {
                mqttPublishService.publish(
                        s -> String.format("train-locations/%s/%s", s.trainLocationId.departureDate, s.trainLocationId.trainNumber),
                        filteredTrainLocations, null);
            } catch (final Exception e) {
                log.error("MQTT updated failed. Still trying to update database.", e);
            }

            trainLocationRepository.persist(filteredTrainLocations);

            final ZonedDateTime end = DateProvider.nowInHelsinki();

            log.info("Updated data for {} trainLocations (total received {}) in {} ms", filteredTrainLocations.size(),
                    trainLocations.size(), end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli());

            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRAIN_LOCATIONS);
        } catch (final Exception e) {
            log.error("Error updating train locations", e);
        }
    }

    private FilterResult filterTrains(final List<TrainLocation> trainLocations) {
        final List<TrainLocation> recentlySeenFiltered = recentlySeenTrainLocationFilter.filter(trainLocations);
        final int droppedRecentlySeen = trainLocations.size() - recentlySeenFiltered.size();

        int droppedIpFallback = 0;
        int droppedOffTrack = 0;

        final List<TrainLocation> afterIpFilter = new ArrayList<>();
        for (final TrainLocation t : recentlySeenFiltered) {
            if (isIpFallbackLocation(t.location)) {
                droppedIpFallback++;
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
                droppedOffTrack++;
            }
        }

        return new FilterResult(result, droppedRecentlySeen, droppedIpFallback, droppedOffTrack);
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

    private record FilterResult(List<TrainLocation> result, int droppedRecentlySeen, int droppedIpFallback, int droppedOffTrack) {}
}
