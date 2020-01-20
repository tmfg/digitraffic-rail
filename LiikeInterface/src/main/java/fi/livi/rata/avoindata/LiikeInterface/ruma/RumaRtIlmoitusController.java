package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RumaRtIlmoitusController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${ruma.rti-status-url}")
    private String rumaRtiStatusUrl;

    @Value("${ruma.rti-detailed-url}")
    private String rumaRtiDetailedUrl;

    @Value("${liike.base-url}")
    private String liikeBaseUrl;

    @Autowired
    private RumaAuthenticationTokenService rumaAuthenticationTokenService;

    @RequestMapping(value = "/avoin/ruma/rti", produces = "application/json")
    @ResponseBody
    public Object getRtis() throws IOException {
        String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();
        String fullUrl = liikeBaseUrl + rumaRtiStatusUrl + "?state=DRAFT&state=SENT&state=REJECTED&state=PASSIVE&state=ACTIVE&state=FINISHED&state=REMOVED";
        log.info("Requesting rti status from {}", fullUrl);
        return getFromRumaWithToken(fullUrl, authenticationToken);
    }

    @RequestMapping(value = "/avoin/ruma/rti/{id}/{version}")
    @ResponseBody
    public Object getRti(@PathVariable String id, @PathVariable long version) throws IOException {
        String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();
        String fullUrl = liikeBaseUrl + String.format(rumaRtiDetailedUrl, id, version) ;
        log.info("Requesting rti version from {}", fullUrl);
        return getFromRumaWithToken(fullUrl, authenticationToken);
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
