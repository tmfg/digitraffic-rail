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
import java.util.stream.IntStream;
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

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
    }

    public RemoteTrackWorkNotificationStatus[] getStatuses() {
        return retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + "/ruma/rti";
            log.info("Requesting TrackWorkNotification statuses from " + fullUrl);
            return restTemplate.getForObject(fullUrl, RemoteTrackWorkNotificationStatus[].class);
        });
    }

    public List<TrackWorkNotification> getTrackWorkNotificationVersions(long id, LongStream versions) {
        return versions.mapToObj(v -> retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + String.format("/ruma/rti/%s/%s", id, v);
            log.info("Requesting TrackWorkNotification version from " + fullUrl);
            return restTemplate.getForObject(fullUrl, TrackWorkNotification.class);
        })).collect(Collectors.toList());
    }

}
