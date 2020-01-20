package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RumaAuthenticationTokenService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${ruma.login-url}")
    private String rumaLoginUrl;

    @Value("${liike.base-url}")
    private String liikeBaseUrl;

    @Value("${ruma.username}")
    private String username;

    @Value("${ruma.password}")
    private String password;

    @Autowired
    private ObjectMapper objectMapper;
    private String token;
    private ZonedDateTime tokenIssuedAt;

    public String getAuthenticationToken() throws IOException {
        if (token == null || tokenIssuedAt.isBefore(ZonedDateTime.now().minusDays(14))) {
            String responseAsString = getTokenAsString();
            JsonNode responseAsNodes = objectMapper.readTree(responseAsString);
            token = responseAsNodes.get("token").asText();
            tokenIssuedAt = ZonedDateTime.now();

            log.info("Renewed RUMA token");
        }
        return token;
    }

    private String getTokenAsString() throws IOException {
        String rawData = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password);
        String type = "application/json";
        URL u = new URL(liikeBaseUrl + rumaLoginUrl);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", type);
        conn.setRequestProperty("Content-Length", String.valueOf(rawData.length()));
        OutputStream os = conn.getOutputStream();
        os.write(rawData.getBytes());

        return new String(conn.getInputStream().readAllBytes(), Charset.forName("utf8"));
    }
}
