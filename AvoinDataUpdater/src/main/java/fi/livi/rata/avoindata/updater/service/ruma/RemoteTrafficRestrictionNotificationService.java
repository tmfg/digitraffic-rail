package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Service
public class RemoteTrafficRestrictionNotificationService {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RestTemplate restTemplate;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
    }

    public RemoteRumaNotificationStatus[] getStatuses() {
        return retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + "/ruma/lri";
            log.info("Requesting TrafficRestrictionNotification statuses from " + fullUrl);
            return restTemplate.getForObject(fullUrl, RemoteRumaNotificationStatus[].class);
        });
    }

    public List<TrafficRestrictionNotification> getTrafficRestrictionNotificationVersions(long id, LongStream versions) {
        return versions.mapToObj(v -> retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + String.format("/ruma/lri/%s/%s", id, v);
            log.info("Requesting TrafficRestrictionNotification version from " + fullUrl);
            return restTemplate.getForObject(fullUrl, TrafficRestrictionNotification.class);
        })).collect(Collectors.toList());
    }

}
