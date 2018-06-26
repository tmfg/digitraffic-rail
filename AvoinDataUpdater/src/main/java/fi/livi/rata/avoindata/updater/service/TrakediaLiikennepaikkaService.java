package fi.livi.rata.avoindata.updater.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class TrakediaLiikennepaikkaService {

    @Autowired
    private ObjectMapper objectMapper;

    private static Logger logger = LoggerFactory.getLogger(TrakediaLiikennepaikkaService.class);

    @Value("${trakedia.url}")
    private String baseUrl;

    @Cacheable("trakediaLiikennepaikka")
    public Map<String, Double[]> getTrakediaLiikennepaikkas() {
        Map<String, Double[]> liikennepaikkaMap = new HashMap<>();

        try {
            String now = LocalDate.now().atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            URL url = new URL(String.format(baseUrl, now, now));

            logger.info("Fetching Trakedia data from {}", url);

            final JsonNode jsonNode = objectMapper.readTree(url);

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
}