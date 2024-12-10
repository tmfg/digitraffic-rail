package fi.livi.rata.avoindata.updater.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

import fi.livi.digitraffic.common.cache.ExpiringCache;

/**
 * infra-api version 0.4 or newer is needed!
 */
@Component
public class TrakediaLiikennepaikkaService {

    @Autowired
    private WebClient webClient;

    private static final Logger logger = LoggerFactory.getLogger(TrakediaLiikennepaikkaService.class);

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Value("${updater.liikennepaikat.url}")
    private String liikennepaikatUrl;

    @Value("${updater.liikennepaikanosat.url}")
    private String liikennepaikanosatUrl;

    @Value("${updater.raideosuudet.url}")
    private String raideosuudetUrl;

    private final ExpiringCache<Map<String, Double[]>> lpCache = new ExpiringCache<>(Duration.ofHours(12));
    private final ExpiringCache<Map<String, JsonNode>> lpNodeCache = new ExpiringCache<>(Duration.ofHours(12));

    public Map<String, Double[]> getTrakediaLiikennepaikkas() {
        return lpCache.get(() -> {
            final var liikennepaikkaMap = fetchLiikennepaikkaMap(liikennepaikatUrl);
            final var liikennepaikkaOsaMap = fetchLiikennepaikkaMap(liikennepaikanosatUrl);
            final var raideosuusMap = fetchRaideosuusMap(raideosuudetUrl);

            liikennepaikkaMap.putAll(liikennepaikkaOsaMap);
            liikennepaikkaMap.putAll(raideosuusMap);

            // only populate cache if all maps have data
            final var cacheable = !liikennepaikkaMap.isEmpty() && !liikennepaikkaOsaMap.isEmpty() && !raideosuusMap.isEmpty();

            return new ExpiringCache.CacheResult<>(cacheable, liikennepaikkaMap);
        });
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

    private Map<String, Double[]> fetchRaideosuusMap(final String url) {
        final Map<String, Double[]> raideosuusMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return raideosuusMap;
        }

        try {
            logger.info("method=fetchRaideosuusMap Fetching Trakedia data from {}", url);

            final JsonNode jsonNode = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).share().block();

            if (jsonNode == null) {
                logger.error("method=fetchRaideosuusMap from {} returned null", url);
                return raideosuusMap;
            }

            for (final JsonNode node : jsonNode) {
                final JsonNode geometria = node.get(0).get("geometria");
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                raideosuusMap.put(lyhenne.asText().toUpperCase(), calculateCenterPoint(geometria));
            }

        } catch(final Exception e) {
            logger.error("method=fetchRaideosuusMap could not fetch Trakedia data", e);
        }

        return raideosuusMap;
    }

    public Map<String, Double[]> fetchLiikennepaikkaMap(final String url) {
        final Map<String, Double[]> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return liikennepaikkaMap;
        }

        try {
            logger.info("method=fetchLiikennepaikkaMap Fetching Trakedia data from {}", url);

            final JsonNode jsonNode = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).share().block();

            if (jsonNode == null) {
                logger.error("method=fetchLiikennepaikkaMap from {} returned null", url);
                return liikennepaikkaMap;
            }

            for (final JsonNode node : jsonNode) {
                final JsonNode virallinenSijainti = node.get(0).get("virallinenSijainti");
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), new Double[]{virallinenSijainti.get(0).asDouble(), virallinenSijainti
                        .get(1).asDouble()});
            }
        } catch (final Exception e) {
            logger.error("method=fetchLiikennepaikkaMap could not fetch Trakedia data", e);
        }

        return liikennepaikkaMap;
    }

    // Data format:
    // JRI -> {ArrayNode} "[{"tunniste":"1.2.245.578.9.01.23456","virallinenSijainti":[496612,6718700],"lyhenne":"Jri","nimiSe":null,"nimiEn":null}]"
    public Map<String, JsonNode> getTrakediaLiikennepaikkaNodes() {
        return lpNodeCache.get(() -> {
            final var liikennepaikkaMap = fetchNodeMap(liikennepaikatUrl);
            final var liikennepaikanOsaMap = fetchNodeMap(liikennepaikanosatUrl);

            liikennepaikkaMap.putAll(liikennepaikanOsaMap);

            // only populate cache if all maps have data
            final var cacheable = !liikennepaikkaMap.isEmpty() && !liikennepaikanOsaMap.isEmpty();
            return new ExpiringCache.CacheResult<>(cacheable, liikennepaikkaMap);
        });
    }

    public Map<String, JsonNode> fetchNodeMap(final String url) {
        final Map<String, JsonNode> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(url)) {
            return liikennepaikkaMap;
        }

        try {
            logger.info("method=fetchNodeMap Fetching Trakedia nodes from {}", url);

            final JsonNode jsonNode = webClient.get().uri(url).retrieve().bodyToMono(JsonNode.class).block();

            if (jsonNode == null) {
                logger.error("method=fetchNodeMap from {} returned null", url);
                return liikennepaikkaMap;
            }

            for (final JsonNode node : jsonNode) {
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), node);
            }
        } catch (final Exception e) {
            logger.error("method=fetchNodeMap could not fetch Trakedia data", e);
        }

        return liikennepaikkaMap;
    }
}
