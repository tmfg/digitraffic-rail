package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class RumaRtIlmoitusController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ruma.rti-status-url}")
    private String rumaRtiStatusUrl;

    @Value("${ruma.rti-detailed-url}")
    private String rumaRtiDetailedUrl;

    @Value("${liike.base-url}")
    private String liikeBaseUrl;

    @Autowired
    private RumaAuthenticationTokenService rumaAuthenticationTokenService;

    private Map<String, Object[]> recentlySeenMap = new HashMap<>();

    @RequestMapping(value = "/avoin/ruma/rti")
    @ResponseBody
    public Object getRtis() throws IOException {
        String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();

        log.info("Requesting rti status from {}", liikeBaseUrl + rumaRtiStatusUrl);

        final JsonNode nodes = objectMapper.readTree(getFromRumaWithToken(liikeBaseUrl + rumaRtiStatusUrl, authenticationToken));

        ZonedDateTime now = ZonedDateTime.now();
        List<JsonNode> output = new ArrayList<>();
        for (JsonNode node : nodes) {
            JsonNode id = node.get("id");
            JsonNode version = node.get("version");
            String key = String.format("%s %s", id, version);
            Object[] value = recentlySeenMap.get(key);
            if (value == null) {
                String url = liikeBaseUrl + String.format(rumaRtiDetailedUrl, id, version);
                JsonNode detailedNodes = objectMapper.readTree(getFromRumaWithToken(url, authenticationToken));
                output.add(detailedNodes);
                recentlySeenMap.put(getRecentlySeenKey(id, version), new Object[]{now, detailedNodes});
            } else {
                output.add((JsonNode) value[1]);
            }
        }

        removeOldEntriesFromRecentlySeen();

        return output;
    }

    private void removeOldEntriesFromRecentlySeen() {
        ZonedDateTime now = ZonedDateTime.now();
        List<String> toBeDeleted = new ArrayList<>();
        for (Map.Entry<String, Object[]> entry : recentlySeenMap.entrySet()) {
            ZonedDateTime timestamp = (ZonedDateTime) entry.getValue()[0];
            if (timestamp.isBefore(now.minusDays(7))) {
                toBeDeleted.add(entry.getKey());
            }
        }

        for (String key : toBeDeleted) {
            recentlySeenMap.remove(key);
        }
    }

    private String getRecentlySeenKey(JsonNode id, JsonNode version) {
        return String.format("%s %s", id, version);
    }

    private String getFromRumaWithToken(String url, String token) throws IOException {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", String.format("Bearer %s", token));
        conn.setRequestProperty("Content-Type", "application/json");

        return new String(conn.getInputStream().readAllBytes(), Charset.forName("utf8"));
    }
}
