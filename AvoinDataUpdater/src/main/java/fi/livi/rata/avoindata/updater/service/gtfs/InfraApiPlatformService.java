package fi.livi.rata.avoindata.updater.service.gtfs;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;

@Component
public class InfraApiPlatformService {

    @Qualifier("normalRestTemplate")
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    private static Logger logger = LoggerFactory.getLogger(InfraApiPlatformService.class);

    @Value("${infra-api.laiturit.url}")
    private String baseUrl;

    public static final Pattern lastTwoLiikennepaikkaIdPlaces = Pattern.compile("\\d+.\\d+$");

    @Cacheable("infraApiPlatformNodes")
    public Map<String, List<InfraApiPlatform>> getPlatformsByLiikennepaikkaIdPart(final ZonedDateTime fromDate, final ZonedDateTime toDate) {
        Map<String, List<InfraApiPlatform>> platformsByLiikennepaikkaIdPart = new HashMap<>();

        try {
            String from = fromDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String to = toDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            URI url = new URI(String.format(baseUrl, from, to));

            logger.info("Fetching Infra-API platform data from {}", url);

            JsonNode jsonNode = restTemplate.getForObject(url, JsonNode.class);

            for (final JsonNode node : jsonNode) {
                InfraApiPlatform platform = deserializePlatform(node.get(0));
                String liikennepaikkaIdPart = extractLiikennepaikkaIdPart(platform.liikennepaikkaId);
                platformsByLiikennepaikkaIdPart.putIfAbsent(liikennepaikkaIdPart, new ArrayList<>());
                platformsByLiikennepaikkaIdPart.get(liikennepaikkaIdPart).add(platform);
            }
        } catch (Exception e) {
            logger.error("Could not fetch Infra-API platform data", e);
        }

        return platformsByLiikennepaikkaIdPart;
    }

    private InfraApiPlatform deserializePlatform(final JsonNode node) {

        String liikennepaikkaId;
        String name;
        String description;
        String commercialTrack;
        Geometry geometry;

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
        GeometryFactory geometryFactory = new GeometryFactory();
        List<LineString> lineStrings = new ArrayList<>();

        geometryNode.elements().forEachRemaining(lineStringElement -> {
            List<Coordinate> lineStringCoordinates = new ArrayList<>();
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
            LineString lineString = geometryFactory.createLineString(lineStringCoordinates.toArray(new Coordinate[lineStringCoordinates.size()]));
            lineStrings.add(lineString);
        });

        return geometryFactory.createMultiLineString(lineStrings.toArray(new LineString[lineStrings.size()]));
    }

    public static String extractLiikennepaikkaIdPart(final String id) {
        Matcher matcher = lastTwoLiikennepaikkaIdPlaces.matcher(id);
        return matcher.find() ? matcher.group() : "";
    }

}
