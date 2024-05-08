package fi.livi.rata.avoindata.updater.service.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.TrackSectionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Service
public class RemoteTrackWorkNotificationService {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RipaService ripaService;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    private static final String rumaUrlFragment = "/ruma/rti";

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
    }

    public RemoteRumaNotificationStatus[] getStatuses() {
        return retryTemplate.execute(context -> ripaService.getFromRipa(rumaUrlFragment, RemoteRumaNotificationStatus[].class));
    }

    public List<TrackWorkNotification> getTrackWorkNotificationVersions(final String id, final LongStream versions) {
        return versions.mapToObj(v -> retryTemplate.execute(context -> {
            final String path = String.format("%s/%s/%s", rumaUrlFragment, id, v);

            return ripaService.getFromRipa(path, TrackWorkNotification.class, MediaType.APPLICATION_JSON_VALUE);
        })).collect(Collectors.toList());
    }
}
