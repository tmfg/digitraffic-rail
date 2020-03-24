package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
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
public class RemoteTrackWorkNotificationService {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RestTemplate restTemplate;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    private static final String rumaUrlFragment = "/ruma/rti";

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
    }

    public RemoteRumaNotificationStatus[] getStatuses() {
        return retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + rumaUrlFragment;
            return restTemplate.getForObject(fullUrl, RemoteRumaNotificationStatus[].class);
        });
    }

    public List<TrackWorkNotification> getTrackWorkNotificationVersions(String id, LongStream versions) {
        return versions.mapToObj(v -> retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + String.format(rumaUrlFragment + "/%s/%s", id, v);
            return restTemplate.getForObject(fullUrl, TrackWorkNotification.class);
        })).collect(Collectors.toList());
    }

}
