package fi.livi.rata.avoindata.updater.updaters;

import static java.util.function.Predicate.not;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationStatus;

@Service
public class TrackWorkNotificationUpdater {

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private RemoteTrackWorkNotificationService remoteTrackWorkNotificationService;
    private LocalTrackWorkNotificationService localTrackWorkNotificationService;

    public TrackWorkNotificationUpdater(
            RemoteTrackWorkNotificationService remoteTrackWorkNotificationService,
            LocalTrackWorkNotificationService localTrackWorkNotificationService) {
        this.remoteTrackWorkNotificationService = remoteTrackWorkNotificationService;
        this.localTrackWorkNotificationService = localTrackWorkNotificationService;
    }

    @Scheduled(fixedDelay = 3600000) // hourly
    protected void update() {
        if (StringUtils.isEmpty(liikeInterfaceUrl)) {
            return;
        }

        RemoteTrackWorkNotificationStatus[] statusesResp = remoteTrackWorkNotificationService.getStatuses();

        if (statusesResp != null && statusesResp.length > 0) {
            Map<Long, Long> statuses = Arrays.stream(statusesResp)
                    .collect(Collectors.toMap(RemoteTrackWorkNotificationStatus::getId, RemoteTrackWorkNotificationStatus::getVersion));
            log.info("Received {} track work notification statuses", statuses.size());
            List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications = localTrackWorkNotificationService.getLocalTrackWorkNotifications(statuses.keySet());
            addNewTrackWorkNotifications(statuses, localTrackWorkNotifications);
            updateTrackWorkNotifications(statuses, localTrackWorkNotifications);
        } else {
            log.error("Error retrieving track work notification statuses or received empty response");
        }
    }

    private void addNewTrackWorkNotifications(Map<Long, Long> statuses, List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications) {
        Collection<Long> newTrackWorkNotifications = CollectionUtils.disjunction(localTrackWorkNotifications.stream().map(LocalTrackWorkNotificationStatus::getId).collect(Collectors.toSet()), statuses.keySet());

        for (Map.Entry<Long, Long> e : statuses.entrySet()) {
            if (newTrackWorkNotifications.contains(e.getKey())) {
                updateTrackWorkNotification(e.getKey(), new TreeSet<>(LongStream.rangeClosed(1, e.getValue()).boxed().collect(Collectors.toList())));
            }
        }
        log.info("Added {} new track work notifications", newTrackWorkNotifications.size());
    }

    private void updateTrackWorkNotifications(Map<Long, Long> statuses, List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications) {
        int updatedCount = localTrackWorkNotifications.stream()
                .map(localTrackWorkNotification -> updateNotificationVersions(localTrackWorkNotification, statuses))
                .filter(not(Boolean::booleanValue))
                .mapToInt(x -> 1)
                .sum();
        log.info("Updated {} track work notifications", updatedCount);
    }

    private boolean updateNotificationVersions(LocalTrackWorkNotificationStatus localTrackWorkNotification, Map<Long, Long> statuses) {
        if (!statuses.containsKey(localTrackWorkNotification.id)) {
            return false;
        }
       long remoteVersion = statuses.get(localTrackWorkNotification.id);
        SortedSet<Long> versions = new TreeSet<>();
        if (localTrackWorkNotification.minVersion > 1) {
            LongStream.range(1, localTrackWorkNotification.minVersion).forEach(versions::add);
        }
        if (remoteVersion > localTrackWorkNotification.maxVersion) {
            LongStream.rangeClosed(localTrackWorkNotification.maxVersion + 1, remoteVersion).forEach(versions::add);
        }
        if (!versions.isEmpty()) {
            updateTrackWorkNotification(localTrackWorkNotification.id, versions);
        }
        return !versions.isEmpty();
    }

    private void updateTrackWorkNotification(long id, SortedSet<Long> versions) {
        log.debug("Updating TrackWorkNotification {}, required versions {}", id, versions);
        List<TrackWorkNotification> trackWorkNotificationVersions = remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(id, versions.stream().mapToLong(Long::longValue));
        log.debug("Got {} versions for TrackWorkNotification {}", trackWorkNotificationVersions.size(), id);
        localTrackWorkNotificationService.saveAll(trackWorkNotificationVersions);
    }

}
