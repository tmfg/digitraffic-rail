package fi.livi.rata.avoindata.updater.service;

import java.util.HashMap;
import java.util.Map;

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

    @Value("${updater.liikennepaikat.url}")
    private String liikennepaikatUrl;

    @Value("${updater.liikennepaikanosat.url}")
    private String liikennepaikanosatUrl;

    @Cacheable("liikennepaikkaCache")
    public Map<String, Double[]> getTrakediaLiikennepaikkas() {
        final var liikennepaikkaMap = fetchLiikennepaikkaMap(liikennepaikatUrl);
        final var liikenepaikkaOsaMap = fetchLiikennepaikkaMap(liikennepaikanosatUrl);

        liikennepaikkaMap.putAll(liikenepaikkaOsaMap);

        return liikennepaikkaMap;
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
