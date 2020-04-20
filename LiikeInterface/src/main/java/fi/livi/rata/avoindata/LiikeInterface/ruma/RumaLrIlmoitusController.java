package fi.livi.rata.avoindata.LiikeInterface.ruma;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RumaLrIlmoitusController extends AbstractRumaController {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${ruma.lri-status-url}")
    private String rumaLriStatusUrl;

    @Value("${ruma.lri-detailed-url}")
    private String rumaLriDetailedUrl;

    @Value("${liike.base-url}")
    private String liikeBaseUrl;

    @Autowired
    private RumaAuthenticationTokenService rumaAuthenticationTokenService;

    @RequestMapping(value = "/avoin/ruma/lri", produces = "application/json")
    @ResponseBody
    public Object getLris(
            @RequestParam final int from
    ) throws IOException {
        String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();
        String fullUrl = liikeBaseUrl + rumaLriStatusUrl + "?state=SENT&state=FINISHED&size=1000&from=" + from;
        log.info("Requesting lri status from {}", fullUrl);
        return getFromRumaWithToken(fullUrl, authenticationToken);
    }

    @RequestMapping(value = "/avoin/ruma/lri/{id}/{version}")
    @ResponseBody
    public Object getLri(@PathVariable String id, @PathVariable long version) throws IOException {
        String authenticationToken = rumaAuthenticationTokenService.getAuthenticationToken();
        String fullUrl = liikeBaseUrl + String.format(rumaLriDetailedUrl, id, version) ;
        log.trace("Requesting lri version from {}", fullUrl);
        return getFromRumaWithToken(fullUrl, authenticationToken);
    }

}
