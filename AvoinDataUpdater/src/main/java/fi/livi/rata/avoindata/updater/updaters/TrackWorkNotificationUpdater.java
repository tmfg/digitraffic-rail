package fi.livi.rata.avoindata.updater.updaters;

import static java.util.function.Predicate.not;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import fi.livi.rata.avoindata.updater.service.ruma.LocalTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalRumaNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrackWorkNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteRumaNotificationStatus;

@Service
public class TrackWorkNotificationUpdater {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final RemoteTrackWorkNotificationService remoteTrackWorkNotificationService;
    private final LocalTrackWorkNotificationService localTrackWorkNotificationService;
    private final LastUpdateService lastUpdateService;
    private final Wgs84ConversionService wgs84ConversionService;
    private final String liikeInterfaceUrl;

    public TrackWorkNotificationUpdater(
            final RemoteTrackWorkNotificationService remoteTrackWorkNotificationService,
            final LocalTrackWorkNotificationService localTrackWorkNotificationService,
            final LastUpdateService lastUpdateService,
            final Wgs84ConversionService wgs84ConversionService,
            final @Value("${updater.liikeinterface-url}") String liikeInterfaceUrl) {
        this.remoteTrackWorkNotificationService = remoteTrackWorkNotificationService;
        this.localTrackWorkNotificationService = localTrackWorkNotificationService;
        this.lastUpdateService = lastUpdateService;
        this.wgs84ConversionService = wgs84ConversionService;
        this.liikeInterfaceUrl = liikeInterfaceUrl;
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    protected void update() {
        if (StringUtils.isEmpty(liikeInterfaceUrl)) {
            return;
        }

        RemoteRumaNotificationStatus[] statusesResp = remoteTrackWorkNotificationService.getStatuses();

        if (statusesResp != null && statusesResp.length > 0) {
            Map<String, Long> statuses = Arrays.stream(statusesResp)
                    .collect(Collectors.toMap(RemoteRumaNotificationStatus::getId, RemoteRumaNotificationStatus::getVersion));
            log.info("Received {} track work notification statuses", statuses.size());
            List<LocalRumaNotificationStatus> localTrackWorkNotifications = localTrackWorkNotificationService.getLocalTrackWorkNotifications(statuses.keySet());
            addNewTrackWorkNotifications(statuses, localTrackWorkNotifications);
            updateTrackWorkNotifications(statuses, localTrackWorkNotifications);
            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRACK_WORK_NOTIFICATIONS);
        } else {
            log.error("Error retrieving track work notification statuses or received empty response");
        }
    }

    private void addNewTrackWorkNotifications(Map<String, Long> statuses, List<LocalRumaNotificationStatus> localTrackWorkNotifications) {
        Collection<String> newTrackWorkNotifications = CollectionUtils.disjunction(localTrackWorkNotifications.stream().map(LocalRumaNotificationStatus::getId).collect(Collectors.toSet()), statuses.keySet());

        for (Map.Entry<String, Long> e : statuses.entrySet()) {
            if (newTrackWorkNotifications.contains(e.getKey())) {
                updateTrackWorkNotification(e.getKey(), new TreeSet<>(LongStream.rangeClosed(1, e.getValue()).boxed().collect(Collectors.toList())));
            }
        }
        log.info("Added {} new track work notifications", newTrackWorkNotifications.size());
    }

    private void updateTrackWorkNotifications(Map<String, Long> statuses, List<LocalRumaNotificationStatus> localTrackWorkNotifications) {
        int updatedCount = localTrackWorkNotifications.stream()
                .map(localTrackWorkNotification -> updateNotificationVersions(localTrackWorkNotification, statuses))
                .filter(not(Boolean::booleanValue))
                .mapToInt(x -> 1)
                .sum();
        log.info("Updated {} track work notifications", updatedCount);
    }

    private boolean updateNotificationVersions(LocalRumaNotificationStatus localTrackWorkNotification, Map<String, Long> statuses) {
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

    private void updateTrackWorkNotification(String id, SortedSet<Long> versions) {
        log.debug("Updating TrackWorkNotification {}, required versions {}", id, versions);
        List<TrackWorkNotification> trackWorkNotificationVersions = remoteTrackWorkNotificationService.getTrackWorkNotificationVersions(id, versions.stream().mapToLong(Long::longValue));
        log.debug("Got {} versions for TrackWorkNotification {}", trackWorkNotificationVersions.size(), id);

        List<TrackWorkNotification> versionsToBeSaved = new ArrayList<>();

        for (TrackWorkNotification twn : trackWorkNotificationVersions) {

            if (twn.state == TrackWorkNotificationState.DRAFT) {
                continue;
            }

            if (twn.state == TrackWorkNotificationState.FINISHED) {
                boolean previousVersionIsDraft = previousVersionIsDraft(twn.id, trackWorkNotificationVersions);
                if (previousVersionIsDraft) {
                    continue;
                }
            }

            twn.locationMap = wgs84ConversionService.liviToWgs84Jts(twn.locationMap);
            twn.locationSchema = wgs84ConversionService.liviToWgs84Jts(twn.locationSchema);

            for (TrackWorkPart twp : twn.trackWorkParts) {

                for (RumaLocation rl : twp.locations) {
                    if (rl.locationMap != null) {
                        rl.locationMap = wgs84ConversionService.liviToWgs84Jts(rl.locationMap);
                    }
                    if (rl.locationSchema != null) {
                        rl.locationSchema = wgs84ConversionService.liviToWgs84Jts(rl.locationSchema);
                    }

                    for (IdentifierRange ir : rl.identifierRanges) {
                        ir.locationMap = wgs84ConversionService.liviToWgs84Jts(ir.locationMap);
                        ir.locationSchema = wgs84ConversionService.liviToWgs84Jts(ir.locationSchema);
                    }

                }

            }

            versionsToBeSaved.add(twn);
        }

        if (!versionsToBeSaved.isEmpty()) {
            localTrackWorkNotificationService.saveAll(versionsToBeSaved);
        }
    }

    private boolean previousVersionIsDraft(TrackWorkNotification.TrackWorkNotificationId id, List<TrackWorkNotification> trackWorkNotificationVersions) {
        long previousVersion = id.version - 1;

        for (TrackWorkNotification twn : trackWorkNotificationVersions) {
            if (twn.id.id.equals(id.id) && twn.id.version.equals(previousVersion) && twn.state == TrackWorkNotificationState.DRAFT) {
                return true;
            }
        }

        return false;
    }

}
