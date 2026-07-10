package fi.livi.rata.avoindata.updater.deserializers;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.proj4j.ProjCoordinate;
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
 * point is stored in the transient {@code liikeLocation} for the near-track filter.
 */
@Component
public class PalaYksikkoDeserializer {
    public static final int MAX_ACCURACY = 32000;

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    /**
     * Parse the full PALA yksikot.json response body into a list of {@link TrainLocation} entities.
     *
     * @param json raw JSON response body from PALA
     * @return list of train locations; empty if the response contains no trains
     */
    public List<TrainLocation> deserialize(final String json) {
        final JsonNode root = JSON_MAPPER.readTree(json);
        final List<TrainLocation> result = new ArrayList<>();

        for (final Map.Entry<String, JsonNode> entry : root.properties()) {
            final JsonNode node = entry.getValue();

            // ASSUMPTION(PALA-Q3): drop units without sijainti.koordinaatti
            final JsonNode sijaintiNode = node.get("sijainti");
            if (sijaintiNode == null || !sijaintiNode.has("koordinaatti")) {
                continue;
            }

            // Skip entries missing required fields (defensive against malformed PALA responses)
            if (!node.has("junanumero") || !node.has("lahtopaiva") || !node.has("aikaleima")) {
                continue;
            }

            final JsonNode koordinaatti = sijaintiNode.get("koordinaatti");
            if (!koordinaatti.isArray() || koordinaatti.size() < 2) {
                continue;
            }

            final TrainLocation trainLocation = deserializeUnit(node, koordinaatti);
            result.add(trainLocation);
        }

        return result;
    }

    private TrainLocation deserializeUnit(final JsonNode node, final JsonNode koordinaatti) {
        final long trainNumber = node.get("junanumero").asLong();
        final LocalDate departureDate = LocalDate.parse(node.get("lahtopaiva").asText());
        final ZonedDateTime timestamp = ZonedDateTime.parse(node.get("aikaleima").asText());

        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.trainLocationId = new TrainLocationId(trainNumber, departureDate, timestamp);

        // ASSUMPTION(PALA-Q2): nopeus null → speed 0
        final JsonNode nopeusNode = node.get("nopeus");
        trainLocation.speed = (nopeusNode == null || nopeusNode.isNull()) ? 0 : nopeusNode.asInt();

        // Coordinates: EPSG:3067 from sijainti.koordinaatti [x, y] (pre-validated by caller)
        final double x = koordinaatti.get(0).asDouble();
        final double y = koordinaatti.get(1).asDouble();

        trainLocation.liikeLocation = geometryFactory.createPoint(new Coordinate(x, y));

        final ProjCoordinate wgs84 = wgs84ConversionService.liviToWgs84(x, y);
        trainLocation.location = geometryFactory.createPoint(new Coordinate(wgs84.x, wgs84.y));

        // isGpsLocation: true when lahdeKupla is non-empty and at least one entry has koordinaatti
        final JsonNode lahdeKupla = node.get("lahdeKupla");
        final boolean hasGpsFix = hasLahdeKuplaWithKoordinaatti(lahdeKupla);
        trainLocation.isGpsLocation = hasGpsFix;

        // Accuracy: from lahdeKupla[0].tarkkuus (mm → m), capped at MAX_ACCURACY; null if no GPS
        // ASSUMPTION(PALA-Q1): accuracy derives from lahdeKupla[].tarkkuus, not top-level epavarmuus
        if (hasGpsFix) {
            final JsonNode tarkkuusNode = lahdeKupla.get(0).get("tarkkuus");
            if (tarkkuusNode != null && !tarkkuusNode.isNull()) {
                final int tarkkuusMm = Math.max(tarkkuusNode.asInt(), 0);
                trainLocation.accuracy = Math.min(tarkkuusMm / 1000, MAX_ACCURACY);
            } else {
                trainLocation.accuracy = null;
            }
        } else {
            trainLocation.accuracy = null;
        }

        return trainLocation;
    }

    private static boolean hasLahdeKuplaWithKoordinaatti(final JsonNode lahdeKupla) {
        if (lahdeKupla == null || !lahdeKupla.isArray() || lahdeKupla.isEmpty()) {
            return false;
        }
        for (final JsonNode entry : lahdeKupla) {
            if (entry.has("koordinaatti")) {
                return true;
            }
        }
        return false;
    }
}
