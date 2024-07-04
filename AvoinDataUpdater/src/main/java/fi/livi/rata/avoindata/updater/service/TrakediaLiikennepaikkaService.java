package fi.livi.rata.avoindata.updater.service;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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

@Component
public class TrakediaLiikennepaikkaService {

    @Autowired
    private WebClient webClient;

    private static Logger logger = LoggerFactory.getLogger(TrakediaLiikennepaikkaService.class);

    @Value("${trakedia.url}")
    private String baseUrl;

    @Cacheable("trakediaLiikennepaikka")
    public Map<String, Double[]> getTrakediaLiikennepaikkas(final ZonedDateTime utcDate) {
        final Map<String, Double[]> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(baseUrl)) {
            return liikennepaikkaMap;
        }

        try {
            logger.info("Fetching Trakedia data from {}", baseUrl);

            final JsonNode jsonNode = webClient.get().uri(baseUrl).retrieve().bodyToMono(JsonNode.class).share().block();

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
    public Map<String, JsonNode> getTrakediaLiikennepaikkaNodes(final ZonedDateTime utcDate) {
        final Map<String, JsonNode> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(baseUrl)) {
            return liikennepaikkaMap;
        }

        try {
            final String now = utcDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            final URI url = new URI(String.format(baseUrl, now, now));

            logger.info("Fetching Trakedia data from {}", url);

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
