package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.ruma.*;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static java.util.function.Predicate.not;

@Service
public class TrafficRestrictionNotificationUpdater {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final RemoteTrafficRestrictionNotificationService remoteTrafficRestrictionNotificationService;
    private final LocalTrafficRestrictionNotificationService localTrafficRestrictionNotificationService;
    private final LastUpdateService lastUpdateService;
    private final Wgs84ConversionService wgs84ConversionService;
    private final String liikeInterfaceUrl;

    public TrafficRestrictionNotificationUpdater(
            final RemoteTrafficRestrictionNotificationService remoteTrafficRestrictionNotificationService,
            final LocalTrafficRestrictionNotificationService localTrafficRestrictionNotificationService,
            final LastUpdateService lastUpdateService,
            final Wgs84ConversionService wgs84ConversionService,
            final @Value("${updater.liikeinterface-url}") String liikeInterfaceUrl) {
        this.remoteTrafficRestrictionNotificationService = remoteTrafficRestrictionNotificationService;
        this.localTrafficRestrictionNotificationService = localTrafficRestrictionNotificationService;
        this.lastUpdateService = lastUpdateService;
        this.wgs84ConversionService = wgs84ConversionService;
        this.liikeInterfaceUrl = liikeInterfaceUrl;
    }

    @Scheduled(fixedDelay = 300000) // every 5 minutes
    protected void update() {
        if (StringUtils.isEmpty(liikeInterfaceUrl)) {
            return;
        }

        RemoteRumaNotificationStatus[] statusesResp = remoteTrafficRestrictionNotificationService.getStatuses();
        log.info("Received {} traffic restriction notification statuses", statusesResp.length);

        if (statusesResp != null && statusesResp.length > 0) {
            Map<String, Long> statuses = Arrays.stream(statusesResp)
                    .collect(Collectors.toMap(RemoteRumaNotificationStatus::getId, RemoteRumaNotificationStatus::getVersion));
            log.info("Received {} traffic restriction notification statuses", statuses.size());
            List<LocalRumaNotificationStatus> localTrafficRestrictionNotifications = localTrafficRestrictionNotificationService.getLocalTrafficRestrictionNotifications(statuses.keySet());
            addNewTrafficRestrictionNotifications(statuses, localTrafficRestrictionNotifications);
            updateTrafficRestrictionNotifications(statuses, localTrafficRestrictionNotifications);
            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRACK_WORK_NOTIFICATIONS);
        } else {
            log.error("Error retrieving traffic restriction notification statuses or received empty response");
        }
 
    }

    private void addNewTrafficRestrictionNotifications(Map<String, Long> statuses, List<LocalRumaNotificationStatus> localTrafficRestrictionNotifications) {
        Collection<String> newTrafficRestrictionNotifications = CollectionUtils.disjunction(localTrafficRestrictionNotifications.stream().map(LocalRumaNotificationStatus::getId).collect(Collectors.toSet()), statuses.keySet());

        for (Map.Entry<String, Long> e : statuses.entrySet()) {
            if (newTrafficRestrictionNotifications.contains(e.getKey())) {
                updateTrafficRestrictionNotification(e.getKey(), new TreeSet<>(LongStream.rangeClosed(1, e.getValue()).boxed().collect(Collectors.toList())));
            }
        }
        log.info("Added {} new traffic restriction notifications", newTrafficRestrictionNotifications.size());
    }

    private void updateTrafficRestrictionNotifications(Map<String, Long> statuses, List<LocalRumaNotificationStatus> localTrafficRestrictionNotifications) {
        int updatedCount = localTrafficRestrictionNotifications.stream()
                .map(localTrafficRestrictionNotification -> updateNotificationVersions(localTrafficRestrictionNotification, statuses))
                .filter(not(Boolean::booleanValue))
                .mapToInt(x -> 1)
                .sum();
        log.info("Updated {} traffic restriction notifications", updatedCount);
    }

    private boolean updateNotificationVersions(LocalRumaNotificationStatus localTrafficRestrictionNotification, Map<String, Long> statuses) {
        if (!statuses.containsKey(localTrafficRestrictionNotification.id)) {
            return false;
        }
       long remoteVersion = statuses.get(localTrafficRestrictionNotification.id);
        SortedSet<Long> versions = new TreeSet<>();
        if (localTrafficRestrictionNotification.minVersion > 1) {
            LongStream.range(1, localTrafficRestrictionNotification.minVersion).forEach(versions::add);
        }
        if (remoteVersion > localTrafficRestrictionNotification.maxVersion) {
            LongStream.rangeClosed(localTrafficRestrictionNotification.maxVersion + 1, remoteVersion).forEach(versions::add);
        }
        if (!versions.isEmpty()) {
            updateTrafficRestrictionNotification(localTrafficRestrictionNotification.id, versions);
        }
        return !versions.isEmpty();
    }

    private void updateTrafficRestrictionNotification(String id, SortedSet<Long> versions) {
        log.info("Updating TrafficRestrictionNotification {}, required versions {}", id, versions);
        final List<TrafficRestrictionNotification> trackWorkNotificationVersions = remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(id, versions.stream().mapToLong(Long::longValue));
        log.info("Got {} versions for TrafficRestrictionNotification {}", trackWorkNotificationVersions.size(), id);

        final List<TrafficRestrictionNotification> versionsToBeSaved = trackWorkNotificationVersions.stream()
                .filter(trn -> {
                    if (trn.state == TrafficRestrictionNotificationState.DRAFT) {
                        return false;
                    }

                    if (trn.state == TrafficRestrictionNotificationState.FINISHED) {
                        boolean previousVersionIsDraft = previousVersionIsDraft(trn.id, trackWorkNotificationVersions);
                        return !previousVersionIsDraft;
                    }

                    return true;
                })
                .peek(trn -> {
                    trn.locationMap = wgs84ConversionService.liviToWgs84Jts(trn.locationMap);
                    trn.locationSchema = wgs84ConversionService.liviToWgs84Jts(trn.locationSchema);

                    trn.locations.forEach(rl -> {
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
                    });
                }).collect(Collectors.toList());

        if (!versionsToBeSaved.isEmpty()) {
            localTrafficRestrictionNotificationService.saveAll(versionsToBeSaved);
        }
    }

    private boolean previousVersionIsDraft(TrafficRestrictionNotification.TrafficRestrictionNotificationId id, List<TrafficRestrictionNotification> trackWorkNotificationVersions) {
        long previousVersion = id.version - 1;

        for (TrafficRestrictionNotification trn : trackWorkNotificationVersions) {
            if (trn.id.id.equals(id.id) && trn.id.version.equals(previousVersion) && trn.state == TrafficRestrictionNotificationState.DRAFT) {
                return true;
            }
        }

        return false;
    }

}
