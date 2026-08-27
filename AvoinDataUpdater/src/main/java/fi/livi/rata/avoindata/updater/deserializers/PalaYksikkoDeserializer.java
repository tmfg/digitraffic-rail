package fi.livi.rata.avoindata.updater.deserializers;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;

/**
 * Deserializes PALA {@code /0.2/yksikot.json} response into {@link TrainLocation} entities.
 *
 * <p>The PALA response is a JSON <b>object</b> keyed by train number string
 * ({@code {"1": {...}, "23": {...}}}), not an array.
 *
 * <p>Coordinates arrive in EPSG:3067 (default) and are converted to WGS84 via
 * {@link Wgs84ConversionService} for the persisted {@code location} field. The original EPSG:3067
 * point is stored in the transient {@code locationEpsg3067} for the near-track filter.
 */
@Component
public class PalaYksikkoDeserializer {
    public static final int MAX_ACCURACY = 32000;

    private static final Logger log = LoggerFactory.getLogger(PalaYksikkoDeserializer.class);
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    /**
     * Convenience for callers (and tests) that only need the successfully parsed locations.
     *
     * @param json raw JSON response body from PALA
     * @return list of train locations; empty if the response contains no trains
     */
    public List<TrainLocation> deserialize(final String json) {
        return deserializeWithStats(json).locations();
    }

    /**
     * Parse the full PALA yksikot.json response body, returning the successfully parsed locations plus the
     * drop/error counts the ingestion wide-event log needs.
     *
     * @param json raw JSON response body from PALA
     * @return the parse result (never {@code null}); throws only if the whole body cannot be parsed
     */
    public PalaDeserializationResult deserializeWithStats(final String json) {
        final JsonNode root = JSON_MAPPER.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("PALA response root is not a JSON object: "
                    + (root == null ? "null" : root.getClass().getSimpleName()));
        }
        final List<TrainLocation> locations = new ArrayList<>();

        int droppedNoCoordinate = 0;
        int droppedNoSpeed = 0;
        int deserializationErrors = 0;
        String firstErrorTrainNumber = null;
        String firstErrorSampleJson = null;

        for (final Map.Entry<String, JsonNode> entry : root.properties()) {
            final String trainNumberKey = entry.getKey();
            final JsonNode node = entry.getValue();

            final JsonNode sijaintiNode = node.get("sijainti");
            // It is possible that there is no coordinate in the PALA response for a train (e.g. if the train is not currently tracked). In that case, we drop the unit and log it as a debug message.
            if (sijaintiNode == null || !sijaintiNode.has("koordinaatti")) {
                droppedNoCoordinate++;
                log.debug("operation=deserializePala rail.entity.type=train_location rail.filter.reason=no_coordinate "
                        + "rail.train.number={}", trainNumberKey);
                continue;
            }

            // It is possible that PALA nopeus is null meaning the "speed is unknown".
            final JsonNode nopeusNode = node.get("nopeus");
            if (nopeusNode == null || nopeusNode.isNull()) {
                droppedNoSpeed++;
                log.debug("operation=deserializePala rail.entity.type=train_location rail.filter.reason=no_speed "
                        + "rail.train.number={}", trainNumberKey);
                continue;
            }

            // Everything below is a data-quality error if it fails: PalaDeserializationException carries the reason
            // and a capped JSON sample. Count it, capture the first sample for diagnostics, and skip the unit — a
            // single bad unit must never blank the whole poll.
            try {
                locations.add(deserializeUnit(trainNumberKey, node, sijaintiNode));
            } catch (final PalaDeserializationException e) {
                deserializationErrors++;
                if (firstErrorTrainNumber == null) {
                    firstErrorTrainNumber = e.getTrainNumber();
                    firstErrorSampleJson = e.getSampleJson();
                }
                log.debug("operation=deserializePala rail.entity.type=train_location rail.error.reason={} "
                        + "rail.train.number={}", e.getReason(), trainNumberKey);
            }
        }

        return new PalaDeserializationResult(locations, root.size(), droppedNoCoordinate, droppedNoSpeed,
                deserializationErrors, firstErrorTrainNumber, firstErrorSampleJson);
    }

    private static void validateRequiredFields(final String trainNumberKey, final JsonNode node) {
        if (!node.has("junanumero") || !node.has("lahtopaiva") || !node.has("aikaleima")) {
            throw new PalaDeserializationException(trainNumberKey, node.toString(), "missing_required_field");
        }
    }

    private static void validateKoordinaatti(final JsonNode koordinaatti, final String trainNumberKey, final JsonNode node) {
        if (koordinaatti == null || !koordinaatti.isArray() || koordinaatti.size() < 2) {
            throw new PalaDeserializationException(trainNumberKey, node.toString(), "malformed_koordinaatti");
        }
    }

    private TrainLocation deserializeUnit(final String trainNumberKey, final JsonNode node, final JsonNode sijaintiNode) {
        validateRequiredFields(trainNumberKey, node);
        final JsonNode koordinaatti = sijaintiNode.get("koordinaatti");
        validateKoordinaatti(koordinaatti, trainNumberKey, node);
        try {
            final long trainNumber = node.get("junanumero").longValue();
            final LocalDate departureDate = LocalDate.parse(node.get("lahtopaiva").stringValue());
            final ZonedDateTime timestamp = ZonedDateTime.parse(node.get("aikaleima").stringValue());

            final TrainLocation trainLocation = new TrainLocation();
            trainLocation.trainLocationId = new TrainLocationId(trainNumber, departureDate, timestamp);

            // Speed is present here — null-speed units are dropped upstream in deserializeWithStats.
            trainLocation.speed = node.get("nopeus").intValue();

            // Coordinates: EPSG:3067 from sijainti.koordinaatti [x, y] (pre-validated by requireKoordinaatti)
            final double x = koordinaatti.get(0).doubleValue();
            final double y = koordinaatti.get(1).doubleValue();

            trainLocation.locationEpsg3067 = geometryFactory.createPoint(new Coordinate(x, y));

            final ProjCoordinate wgs84 = wgs84ConversionService.liviToWgs84(x, y);
            trainLocation.location = geometryFactory.createPoint(new Coordinate(wgs84.x, wgs84.y));

            // isGpsLocation and accuracy come from the SAME source: the first lahdeKupla entry that carries a usable
            // [x, y] GPS coordinate. If several qualify, the first (array order) wins.
            final JsonNode gpsSource = firstGpsSource(node.get("lahdeKupla"));
            trainLocation.isGpsLocation = gpsSource != null;
            trainLocation.accuracy = gpsSource == null ? null : accuracyMetres(gpsSource);

            return trainLocation;
        } catch (final RuntimeException e) {
            throw new PalaDeserializationException(trainNumberKey, node.toString(), e);
        }
    }

    /** First {@code lahdeKupla} entry whose {@code koordinaatti} is a non-null [x, y] numeric array, or {@code null}. */
    private static JsonNode firstGpsSource(final JsonNode lahdeKupla) {
        if (lahdeKupla == null || !lahdeKupla.isArray()) {
            return null;
        }
        for (final JsonNode entry : lahdeKupla) {
            if (hasNumericCoordinate(entry.get("koordinaatti"))) {
                return entry;
            }
        }
        return null;
    }

    private static boolean hasNumericCoordinate(final JsonNode koordinaatti) {
        return koordinaatti != null && koordinaatti.isArray() && koordinaatti.size() >= 2
                && koordinaatti.get(0).isNumber() && koordinaatti.get(1).isNumber();
    }

    /**
     * GPS accuracy in metres from a source's {@code tarkkuus} (mm), floored at 0 and capped at {@link #MAX_ACCURACY};
     * {@code null} when the source has no {@code tarkkuus}. A non-numeric {@code tarkkuus} throws (via strict
     * {@code intValue()}) and is counted as a deserialization error by the caller.
     */
    private static Integer accuracyMetres(final JsonNode gpsSource) {
        final JsonNode tarkkuusNode = gpsSource.get("tarkkuus");
        if (tarkkuusNode == null || tarkkuusNode.isNull()) {
            return null;
        }
        final int tarkkuusMm = Math.max(tarkkuusNode.intValue(), 0);
        return Math.min(tarkkuusMm / 1000, MAX_ACCURACY);
    }
}
