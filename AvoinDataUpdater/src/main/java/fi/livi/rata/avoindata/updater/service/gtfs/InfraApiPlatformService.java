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

import com.fasterxml.jackson.databind.JsonNode;

import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;

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
    public Map<String, List<InfraApiPlatform>> getPlatformsByLiikennepaikkaIdPart(final ZonedDateTime fromDate, final ZonedDateTime toDate) {
        final Map<String, List<InfraApiPlatform>> platformsByLiikennepaikkaIdPart = new HashMap<>();

        try {
            logger.info("Fetching Infra-API platform data from {}", baseUrl);

            final JsonNode jsonNode = webClient.get().uri(baseUrl).retrieve().bodyToMono(JsonNode.class).block();

            for (final JsonNode node : jsonNode) {
                final InfraApiPlatform platform = deserializePlatform(node.get(0));
                final String liikennepaikkaIdPart = extractLiikennepaikkaIdPart(platform.liikennepaikkaId);
                platformsByLiikennepaikkaIdPart.putIfAbsent(liikennepaikkaIdPart, new ArrayList<>());
                platformsByLiikennepaikkaIdPart.get(liikennepaikkaIdPart).add(platform);
            }
        } catch (final Exception e) {
            logger.error("Could not fetch Infra-API platform data", e);
        }

        return platformsByLiikennepaikkaIdPart;
    }

    private InfraApiPlatform deserializePlatform(final JsonNode node) {

        final String liikennepaikkaId;
        final String name;
        final String description;
        final String commercialTrack;
        final Geometry geometry;

        final JsonNode rautatieliikennepaikka = node.get("rautatieliikennepaikka");
        if (!rautatieliikennepaikka.isNull()) {
            liikennepaikkaId = rautatieliikennepaikka.asText();
        } else {
            final JsonNode liikennepaikanOsa = node.get("liikennepaikanOsa");
            liikennepaikkaId = liikennepaikanOsa.isNull() ?
                               "" : liikennepaikanOsa.asText();
        }

        name = node.get("tunnus").asText();
        description = node.get("kuvaus").asText();
        commercialTrack = node.get("kaupallinenNumero").asText();

        final JsonNode geometria = node.get("geometria");
        final MultiLineString platformGeometry = deserializePlatformGeometry(geometria);
        geometry = wgs84ConversionService.liviToWgs84Jts(platformGeometry);

        return new InfraApiPlatform(liikennepaikkaId, name, description, commercialTrack, geometry);
    }

    public MultiLineString deserializePlatformGeometry(final JsonNode geometryNode) {
        final GeometryFactory geometryFactory = new GeometryFactory();
        final List<LineString> lineStrings = new ArrayList<>();

        geometryNode.elements().forEachRemaining(lineStringElement -> {
            final List<Coordinate> lineStringCoordinates = new ArrayList<>();
            if (lineStringElement.isArray()) {
                lineStringElement.elements().forEachRemaining(coordinateElement -> {
                    if (coordinateElement.isArray()) {
                        lineStringCoordinates.add(new Coordinate(coordinateElement.get(0).asDouble(), coordinateElement.get(1).asDouble()));
                    } else {
                        logger.warn("Could not parse platform geometry: expected array, got {}", coordinateElement.getNodeType());
                    }
                });
            } else {
                logger.warn("Could not parse platform geometry: expected array, got {}", lineStringElement.getNodeType());
            }
            final LineString lineString = geometryFactory.createLineString(lineStringCoordinates.toArray(new Coordinate[lineStringCoordinates.size()]));
            lineStrings.add(lineString);
        });

        return geometryFactory.createMultiLineString(lineStrings.toArray(new LineString[lineStrings.size()]));
    }

    public static String extractLiikennepaikkaIdPart(final String id) {
        final Matcher matcher = lastTwoLiikennepaikkaIdPlaces.matcher(id);
        return matcher.find() ? matcher.group() : "";
    }

}
