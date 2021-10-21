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
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

@Component
public class TrakediaLiikennepaikkaService {

    @Autowired
    private RestTemplate restTemplate;

    private static Logger logger = LoggerFactory.getLogger(TrakediaLiikennepaikkaService.class);

    @Value("${trakedia.url}")
    private String baseUrl;

    @Cacheable("trakediaLiikennepaikka")
    public Map<String, Double[]> getTrakediaLiikennepaikkas(ZonedDateTime utcDate) {
        Map<String, Double[]> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(baseUrl)) {
            return liikennepaikkaMap;
        }

        try {
            String now = utcDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            URI url = new URI(String.format(baseUrl, now, now));

            logger.info("Fetching Trakedia data from {}", url);

            JsonNode jsonNode = restTemplate.getForObject(url, JsonNode.class);

            for (final JsonNode node : jsonNode) {
                final JsonNode virallinenSijainti = node.get(0).get("virallinenSijainti");
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), new Double[]{virallinenSijainti.get(0).asDouble(), virallinenSijainti
                        .get(1).asDouble()});
            }
        } catch (Exception e) {
            logger.error("could not fetch Trakedia data", e);
        }

        return liikennepaikkaMap;
    }

    @Cacheable("trakediaLiikennepaikkaNodes")
    public Map<String, JsonNode> getTrakediaLiikennepaikkaNodes(ZonedDateTime utcDate) {
        Map<String, JsonNode> liikennepaikkaMap = new HashMap<>();

        if (Strings.isNullOrEmpty(baseUrl)) {
            return liikennepaikkaMap;
        }

        try {
            String now = utcDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            URI url = new URI(String.format(baseUrl, now, now));

            logger.info("Fetching Trakedia data from {}", url);

            JsonNode jsonNode = restTemplate.getForObject(url, JsonNode.class);

            for (final JsonNode node : jsonNode) {
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), node);
            }
        } catch (Exception e) {
            logger.error("could not fetch Trakedia data", e);
        }

        return liikennepaikkaMap;
    }
}
