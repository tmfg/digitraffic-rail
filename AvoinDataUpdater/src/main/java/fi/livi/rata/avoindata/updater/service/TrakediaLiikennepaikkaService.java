package fi.livi.rata.avoindata.updater.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

/**
 * infra-api version 0.4 or newer is needed!
 */
@Component
public class TrakediaLiikennepaikkaService {

    @Autowired
    private WebClient webClient;

    private static Logger logger = LoggerFactory.getLogger(TrakediaLiikennepaikkaService.class);

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Value("${updater.liikennepaikat.url}")
    private String liikennepaikatUrl;

    @Value("${updater.liikennepaikanosat.url}")
    private String liikennepaikanosatUrl;

    @Value("${updater.raideosuudet.url}")
    private String raideosuudetUrl;

    @Cacheable("liikennepaikkaCache")
    public Map<String, Double[]> getTrakediaLiikennepaikkas() {
        final var liikennepaikkaMap = fetchLiikennepaikkaMap(liikennepaikatUrl);
        final var liikennepaikkaOsaMap = fetchLiikennepaikkaMap(liikennepaikanosatUrl);
        final var raideosuusMap = fetchRaideosuusMap(raideosuudetUrl);

        liikennepaikkaMap.putAll(liikennepaikkaOsaMap);
        liikennepaikkaMap.putAll(raideosuusMap);

        return liikennepaikkaMap;
    }

    private Map<String, Double[]> fetchRaideosuusMap(final String url) {
        final Map<String, Double[]> raideosuusMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return raideosuusMap;
        }

        try {
            logger.info("Fetching Trakedia data from {}", url);

            final JsonNode jsonNode = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).share().block();

            for (final JsonNode node : jsonNode) {
                final JsonNode geometria = node.get(0).get("geometria");
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                raideosuusMap.put(lyhenne.asText().toUpperCase(), calculateCenterPoint(geometria));
            }

        } catch(final Exception e) {
            logger.error("could not fetch Trakedia data", e);
        }

        return raideosuusMap;
    }

    private Double[] calculateCenterPoint(final JsonNode geometria) {
        final List<Coordinate> coordinates = new ArrayList<>();

        for(final JsonNode node : geometria.get(0)) {
            coordinates.add(new Coordinate(node.get(0).asDouble(), node.get(1).asDouble()));
        }

        final LineString lineString = geometryFactory.createLineString(coordinates.toArray(new Coordinate[]{}));
        final Point centroid = lineString.getCentroid();

        // too much precision, remove decimals
        final long x = (long)centroid.getX();
        final long y = (long)centroid.getY();
        return new Double[]{(double)x, (double)y};
    }

    public Map<String, Double[]> fetchLiikennepaikkaMap(final String url) {
        final Map<String, Double[]> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return liikennepaikkaMap;
        }

        try {
            logger.info("Fetching Trakedia data from {}", url);

            final JsonNode jsonNode = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).share().block();

            for (final JsonNode node : jsonNode) {
                final JsonNode virallinenSijainti = node.get(0).get("virallinenSijainti");
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), new Double[]{virallinenSijainti.get(0).asDouble(), virallinenSijainti
                        .get(1).asDouble()});
            }
        } catch (final Exception e) {
            logger.error("could not fetch Trakedia data", e);
        }

        return liikennepaikkaMap;
    }

    @Cacheable("trakediaLiikennepaikkaNodes")
    public Map<String, JsonNode> getTrakediaLiikennepaikkaNodes() {
        final var liikennepaikkaMap = fetchNodeMap(liikennepaikatUrl);
        final var liikennepaikanOsaMap = fetchNodeMap(liikennepaikanosatUrl);

        liikennepaikkaMap.putAll(liikennepaikanOsaMap);

        return liikennepaikkaMap;
    }

    public Map<String, JsonNode> fetchNodeMap(final String url) {
        final Map<String, JsonNode> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return liikennepaikkaMap;
        }

        try {
            logger.info("Fetching Trakedia nodes from {}", url);

            final JsonNode jsonNode = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block();

            for (final JsonNode node : jsonNode) {
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), node);
            }
        } catch (final Exception e) {
            logger.error("could not fetch Trakedia data", e);
        }

        return liikennepaikkaMap;
    }
}
