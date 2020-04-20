package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import static fi.livi.rata.avoindata.LiikeInterface.config.JacksonConfig.ISO_FIXED_FORMAT;

@Controller
public class RumaRtIlmoitusController extends AbstractRumaController {
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
    public Object getRtis(
            @RequestParam final int from,
            @RequestParam("lastupdate") final String lastupdateStr
    ) throws IOException {
        final String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();

        final ZonedDateTime now = ZonedDateTime.now();

        // check updates 1 hour backwards in case multiple updates fail
        final String interval = ISO_FIXED_FORMAT.format(ZonedDateTime.parse(lastupdateStr).minusHours(1)) + "/" + ISO_FIXED_FORMAT.format(now);

        final String fullUrl = liikeBaseUrl + rumaRtiStatusUrl + "?state=SENT&state=PASSIVE&state=ACTIVE&state=FINISHED&size=1000&from=" + from + "&changes=" + interval;
        log.info("Requesting rti status from {}", fullUrl);
        return getFromRumaWithToken(fullUrl, authenticationToken);
    }

    @RequestMapping(value = "/avoin/ruma/rti/{id}/{version}")
    @ResponseBody
    public Object getRti(@PathVariable String id, @PathVariable long version) throws IOException {
        String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();
        String fullUrl = liikeBaseUrl + String.format(rumaRtiDetailedUrl, id, version) ;
        log.trace("Requesting rti version from {}", fullUrl);
        return getFromRumaWithToken(fullUrl, authenticationToken);
    }

}
