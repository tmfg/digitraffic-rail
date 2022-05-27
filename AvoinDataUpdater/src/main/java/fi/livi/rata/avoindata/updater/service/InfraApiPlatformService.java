package fi.livi.rata.avoindata.updater.service;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
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

    @Cacheable("infraApiPlatformNodes")
    public List<InfraApiPlatform> getInfraApiPlatformNodes(ZonedDateTime fromDate, ZonedDateTime toDate) {
        List<InfraApiPlatform> infraApiPlatforms = new ArrayList<>();

        try {
            String from = fromDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String to = toDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            URI url = new URI(String.format(baseUrl, from, to));

            logger.info("Fetching Infra-API platform data from {}", url);

            JsonNode jsonNode = restTemplate.getForObject(url, JsonNode.class);

            for (final JsonNode node : jsonNode) {
                InfraApiPlatform platform = deserializePlatform(node.get(0));
                infraApiPlatforms.add(platform);
            }
        } catch (Exception e) {
            logger.error("could not fetch Infra-API platform data", e);
        }

        return infraApiPlatforms;
    }

    private InfraApiPlatform deserializePlatform(JsonNode node) {
        InfraApiPlatform platform = new InfraApiPlatform();

        final JsonNode rautatieliikennepaikka = node.get("rautatieliikennepaikka");
        if (!rautatieliikennepaikka.isNull()) {
            platform.liikennepaikkaId = rautatieliikennepaikka.textValue();
        } else {
            final JsonNode liikennepaikanOsa = node.get("liikennepaikanOsa");
            platform.liikennepaikkaId = liikennepaikanOsa.isNull() ?
                                        "" : liikennepaikanOsa.textValue();
        }

        platform.name = node.get("tunnus").textValue();
        platform.description = node.get("kuvaus").textValue();
        platform.commercialTrack = node.get("kaupallinenNumero").textValue();

        GeometryFactory geometryFactory = new GeometryFactory();
        List<LineString> platformLineStrings = new ArrayList<>();

        final JsonNode geometria = node.get("geometria");

        geometria.elements().forEachRemaining(lineStringElement -> {
            List<Coordinate> lineStringCoordinates = new ArrayList<>();
            if (lineStringElement.isArray()) {
                lineStringElement.elements().forEachRemaining(coordinateElement -> {
                    if (coordinateElement.isArray()) {
                        lineStringCoordinates.add(new Coordinate(coordinateElement.get(0).asDouble(), coordinateElement.get(1).asDouble()));
                    }
                });
            }
            LineString lineString = geometryFactory.createLineString(lineStringCoordinates.toArray(new Coordinate[lineStringCoordinates.size()]));
            platformLineStrings.add(lineString);
        });

        MultiLineString platformGeometry = geometryFactory.createMultiLineString(platformLineStrings.toArray(new LineString[platformLineStrings.size()]));
        platform.geometry = wgs84ConversionService.liviToWgs84Jts(platformGeometry);

        return platform;
    }

}
