package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;
import tools.jackson.databind.JsonNode;

@Component
public class InfraApiPlatformService {

    @Autowired
    private WebClient webClient;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    private static final Logger logger = LoggerFactory.getLogger(InfraApiPlatformService.class);

    @Value("${infra-api.laiturit.url}")
    private String baseUrl;

    public static final Pattern lastTwoLiikennepaikkaIdPlaces = Pattern.compile("\\d+.\\d+$");

    @Cacheable("infraApiPlatformNodes")
    public Map<String, List<InfraApiPlatform>> getPlatformsByLiikennepaikkaIdPart(final ZonedDateTime fromDate,
            final ZonedDateTime toDate) {
        final Map<String, List<InfraApiPlatform>> platformsByLiikennepaikkaIdPart = new HashMap<>();

        try {
            logger.info("Fetching Infra-API platform data from {}", baseUrl);

            final JsonNode jsonNode = webClient.get().uri(baseUrl).retrieve().bodyToMono(JsonNode.class).block();

            for (final JsonNode node : jsonNode) {
                // Parse each platform independently so one malformed record does not discard
                // the whole batch.
                try {
                    final InfraApiPlatform platform = deserializePlatform(node.get(0));
                    final String liikennepaikkaIdPart = extractLiikennepaikkaIdPart(platform.liikennepaikkaId);
                    platformsByLiikennepaikkaIdPart.putIfAbsent(liikennepaikkaIdPart, new ArrayList<>());
                    platformsByLiikennepaikkaIdPart.get(liikennepaikkaIdPart).add(platform);
                } catch (final Exception e) {
                    logger.warn(
                            "method=getPlatformsByLiikennepaikkaIdPart Could not parse Infra-API platform data for platform {}",
                            node.path(0).path("tunnus").asString(), e);
                }
            }
        } catch (final Exception e) {
            logger.error("Could not fetch Infra-API platform data", e);
        }

        return platformsByLiikennepaikkaIdPart;
    }

    InfraApiPlatform deserializePlatform(final JsonNode node) {
        final String liikennepaikkaId;
        final String name;
        final String description;
        final String commercialTrack;
        final Geometry geometry;

        // Infra-API omits the key entirely when there is no value, so use path() to get
        // a MissingNode instead of null.
        final JsonNode liikennepaikanOsa = node.path("liikennepaikanOsa");

        if (!liikennepaikanOsa.isMissingNode() && !liikennepaikanOsa.isNull()) {
            liikennepaikkaId = liikennepaikanOsa.asString();
        } else {
            final JsonNode rautatieliikennepaikka = node.path("rautatieliikennepaikka");
            liikennepaikkaId = rautatieliikennepaikka.isMissingNode() || rautatieliikennepaikka.isNull() ? ""
                    : rautatieliikennepaikka.asString();
        }

        name = node.path("tunnus").asString();
        description = node.path("kuvaus").asString();
        commercialTrack = node.path("kaupallinenNumero").asString();

        final MultiLineString platformGeometry = deserializePlatformGeometry(node.path("geometria"));
        geometry = wgs84ConversionService.liviToWgs84Jts(platformGeometry);

        return new InfraApiPlatform(liikennepaikkaId, name, description, commercialTrack, geometry);
    }

    public MultiLineString deserializePlatformGeometry(final JsonNode geometryNode) {
        final GeometryFactory geometryFactory = new GeometryFactory();
        final List<LineString> lineStrings = new ArrayList<>();

        geometryNode.forEach(lineStringElement -> {
            final List<Coordinate> lineStringCoordinates = new ArrayList<>();
            if (lineStringElement.isArray()) {
                lineStringElement.forEach(coordinateElement -> {
                    if (coordinateElement.isArray()) {
                        lineStringCoordinates.add(new Coordinate(coordinateElement.get(0).asDouble(),
                                coordinateElement.get(1).asDouble()));
                    } else {
                        logger.warn("Could not parse platform geometry: expected array, got {}",
                                coordinateElement.getNodeType());
                    }
                });
            } else {
                logger.warn("Could not parse platform geometry: expected array, got {}",
                        lineStringElement.getNodeType());
            }
            final LineString lineString = geometryFactory
                    .createLineString(lineStringCoordinates.toArray(new Coordinate[lineStringCoordinates.size()]));
            lineStrings.add(lineString);
        });

        return geometryFactory.createMultiLineString(lineStrings.toArray(new LineString[lineStrings.size()]));
    }

    public static String extractLiikennepaikkaIdPart(final String id) {
        final Matcher matcher = lastTwoLiikennepaikkaIdPlaces.matcher(id);
        return matcher.find() ? matcher.group() : "";
    }

}
