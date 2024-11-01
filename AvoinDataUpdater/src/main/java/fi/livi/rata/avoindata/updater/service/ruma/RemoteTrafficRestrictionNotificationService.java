package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Service
public class RemoteTrafficRestrictionNotificationService {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected WebClient ripaWebClient;

    private static final String rumaUrlFragment = "ruma/lri";

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
    }

    public RemoteRumaNotificationStatus[] getStatuses() {
        return retryTemplate.execute(context -> {
            log.info("Requesting TrafficRestrictionNotification statuses from " + rumaUrlFragment);

            return ripaWebClient.get().uri(rumaUrlFragment).retrieve().bodyToMono(RemoteRumaNotificationStatus[].class).block();
        });
    }

    public List<TrafficRestrictionNotification> getTrafficRestrictionNotificationVersions(final String id, final LongStream versions) {
        return versions.mapToObj(v -> retryTemplate.execute(context -> {
            final String path = String.format("%s/%s/%s", rumaUrlFragment, id, v);
            log.info("Requesting TrafficRestrictionNotification version from " + path);

            return ripaWebClient.get().uri(path)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve().bodyToMono(TrafficRestrictionNotification.class).block();
        })).collect(Collectors.toList());
    }

}
