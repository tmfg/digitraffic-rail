package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.function.Predicate.not;

@Service
public class TrackWorkNotificationUpdater {

    private Logger log = LoggerFactory.getLogger(this.getClass());
    private RemoteTrackWorkNotificationService remoteTrackWorkNotificationService;
    private LocalTrackWorkNotificationService localTrackWorkNotificationService;

    public TrackWorkNotificationUpdater(
            RemoteTrackWorkNotificationService remoteTrackWorkNotificationService,
            LocalTrackWorkNotificationService localTrackWorkNotificationService) {
        this.remoteTrackWorkNotificationService = remoteTrackWorkNotificationService;
        this.localTrackWorkNotificationService = localTrackWorkNotificationService;
    }

    @PostConstruct
    private void init() {
        new SimpleAsyncTaskExecutor().execute(this::update);
    }

    @Scheduled(fixedDelay = 100000 * 30L)
    protected void update() {
        RemoteTrackWorkNotificationStatus[] statusesResp = remoteTrackWorkNotificationService.getStatuses();

        if (statusesResp != null && statusesResp.length > 0) {
            Map<Integer, Integer> statuses = Arrays.stream(statusesResp)
                    .collect(Collectors.toMap(RemoteTrackWorkNotificationStatus::getId, RemoteTrackWorkNotificationStatus::getVersion));
            log.info("Received {} track work notification statuses", statuses.size());
            List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications = localTrackWorkNotificationService.getLocalTrackWorkNotifications(statuses.keySet());
            addNewTrackWorkNotifications(statuses, localTrackWorkNotifications);
            updateTrackWorkNotifications(statuses, localTrackWorkNotifications);
        } else {
            log.error("Error retrieving track work notification statuses or received empty response");
        }
    }

    private void addNewTrackWorkNotifications(Map<Integer, Integer> statuses, List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications) {
        Collection<Integer> newTrackWorkNotifications = CollectionUtils.disjunction(localTrackWorkNotifications.stream().map(LocalTrackWorkNotificationStatus::getId).collect(Collectors.toSet()), statuses.keySet());

        for (Map.Entry<Integer, Integer> e : statuses.entrySet()) {
            if (newTrackWorkNotifications.contains(e.getKey())) {
                updateTrackWorkNotification(e.getKey(), new TreeSet<>(IntStream.rangeClosed(1, e.getValue()).boxed().collect(Collectors.toList())));
            }
        }
        log.info("Added {} new track work notifications", newTrackWorkNotifications.size());
    }

    private void updateTrackWorkNotifications(Map<Integer, Integer> statuses, List<LocalTrackWorkNotificationStatus> localTrackWorkNotifications) {
        int updatedCount = localTrackWorkNotifications.stream()
                .map(localTrackWorkNotification -> getVersions(localTrackWorkNotification, statuses))
                .filter(not(SortedSet::isEmpty))
                .mapToInt(x -> 1)
                .sum();
        log.info("Updated {} track work notifications", updatedCount);
    }

    private SortedSet<Integer> getVersions(LocalTrackWorkNotificationStatus localTrackWorkNotification, Map<Integer, Integer> statuses) {
        if (!statuses.containsKey(localTrackWorkNotification.id)) {
            return Collections.emptySortedSet();
        }
        int remoteVersion = statuses.get(localTrackWorkNotification.id);
        SortedSet<Integer> versions = new TreeSet<>();
        if (localTrackWorkNotification.minVersion > 1) {
            IntStream.range(1, localTrackWorkNotification.minVersion).forEach(versions::add);
        }
        if (remoteVersion > localTrackWorkNotification.maxVersion) {
            IntStream.rangeClosed(localTrackWorkNotification.maxVersion + 1, remoteVersion).forEach(versions::add);
        }
        if (!versions.isEmpty()) {
            updateTrackWorkNotification(localTrackWorkNotification.id, versions);
        }
        return versions;
    }

    private void updateTrackWorkNotification(int id, SortedSet<Integer> versions) {
        log.debug("Updating TrackWorkNotification {}, required versions {}", id, versions);
        List<TrackWorkNotification> trackWorkNotificationVersions = remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(id, versions.stream().mapToInt(Integer::intValue));
        log.debug("Got {} versions for TrackWorkNotification {}", trackWorkNotificationVersions.size(), id);
        localTrackWorkNotificationService.saveAll(trackWorkNotificationVersions);
    }

}
