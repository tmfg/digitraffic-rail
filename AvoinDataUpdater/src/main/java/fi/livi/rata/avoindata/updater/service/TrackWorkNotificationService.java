package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationIdAndVersion;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TrackWorkNotificationService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RestTemplate restTemplate;

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
    }

    public void update() {
        RemoteTrackWorkNotificationStatus[] statusesResp = retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + "/ruma/rti";
            log.info("Requesting TrackWorkNotification statuses from " + fullUrl);
            return restTemplate.getForObject(fullUrl, RemoteTrackWorkNotificationStatus[].class);
        });

        if (statusesResp != null) {
            doUpdate(statusesResp);
        } else {
            log.error("Error retrieving track work notification statuses");
        }
    }

    private void doUpdate(RemoteTrackWorkNotificationStatus[] statusesResp) {
        Map<Integer, Integer> statuses = Arrays.stream(statusesResp).collect(Collectors.toMap(RemoteTrackWorkNotificationStatus::getId, RemoteTrackWorkNotificationStatus::getVersion));
        log.info("Received {} track work notification statuses", statuses.size());

        List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications = getLocalTrackWorkNotifications(statuses);

        addNewTrackWorkNotifications(statuses, localTrackWorkNotifications);
        updateTrackWorkNotifications(statuses, localTrackWorkNotifications);
    }

    private void updateTrackWorkNotifications(Map<Integer, Integer> statuses, List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications) {
        int updatedCount = localTrackWorkNotifications.stream()
                .map(localTrackWorkNotification -> {
                    if (!statuses.containsKey(localTrackWorkNotification.id)) {
                        return 0;
                    }
                    int remoteVersion = statuses.get(localTrackWorkNotification.id);
                    List<Integer> versions = new ArrayList<>();
                    if (localTrackWorkNotification.minVersion > 1) {
                        IntStream.range(1, localTrackWorkNotification.minVersion).forEach(versions::add);
                    }
                    if (remoteVersion > localTrackWorkNotification.maxVersion) {
                        IntStream.rangeClosed(localTrackWorkNotification.maxVersion + 1, remoteVersion).forEach(versions::add);
                    }
                    if (!versions.isEmpty()) {
                        updateTrackWorkNotification(localTrackWorkNotification.id, versions);
                        return 1;
                    }
                    return 0;
                })
                .mapToInt(Integer::intValue)
                .sum();
        log.info("Updated {} track work notifications", updatedCount);
    }

    private void addNewTrackWorkNotifications(Map<Integer, Integer> statuses, List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications) {
        Collection<Integer> newTrackWorkNotifications = CollectionUtils.disjunction(localTrackWorkNotifications.stream().map(LocalTrackWorkNotificationStatus::getId).collect(Collectors.toSet()), statuses.keySet());

        for (Map.Entry<Integer, Integer> e : statuses.entrySet()) {
            if (newTrackWorkNotifications.contains(e.getKey())) {
                updateTrackWorkNotification(e.getKey(), IntStream.rangeClosed(1, e.getValue()).boxed().collect(Collectors.toList()));
            }
        }
        log.info("Added {} new track work notifications", newTrackWorkNotifications.size());
    }

    private List<LocalTrackWorkNotificationStatus> getLocalTrackWorkNotifications(Map<Integer, Integer> statuses) {
        return trackWorkNotificationRepository.findIdsAndVersions(statuses.keySet())
                .stream()
                .collect(Collectors.groupingBy(TrackWorkNotificationIdAndVersion::getId, Collectors.mapping(TrackWorkNotificationIdAndVersion::getVersion, Collectors.toList())))
                .entrySet()
                .stream()
                .map(e -> new LocalTrackWorkNotificationStatus(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Transactional
    void updateTrackWorkNotification(int id, List<Integer> versions) {
        log.debug("Updating TrackWorkNotification {}, required versions {}", id, versions);
        List<TrackWorkNotification> TrackWorkNotificationVersions = fetchTrackWorkNotificationVersions(id, versions.stream().mapToInt(Integer::intValue));
        log.debug("Got {} versions for TrackWorkNotification {}", TrackWorkNotificationVersions.size(), id);
        trackWorkNotificationRepository.saveAll(TrackWorkNotificationVersions);
    }

    private List<TrackWorkNotification> fetchTrackWorkNotificationVersions(int TrackWorkNotificationId, IntStream versions) {
        return versions.mapToObj(v -> retryTemplate.execute(context -> {
            String fullUrl = liikeInterfaceUrl + String.format("/ruma/rti/%s/%s", TrackWorkNotificationId, v);
            log.info("Requesting TrackWorkNotification version from " + fullUrl);
            return restTemplate.getForObject(fullUrl, TrackWorkNotification.class);
        })).collect(Collectors.toList());
    }

    static class RemoteTrackWorkNotificationStatus {
        public int id;
        public int version;

        public int getId() {
            return id;
        }

        public int getVersion() {
            return version;
        }
    }

    static class LocalTrackWorkNotificationStatus {

        public final int id;
        public final int minVersion;
        public final int maxVersion;

        public LocalTrackWorkNotificationStatus(int id, List<Integer> versions) {
            this.id = id;
            this.minVersion = Collections.min(versions);
            this.maxVersion = Collections.max(versions);

        }

        public int getId() {
            return id;
        }
    }
}
