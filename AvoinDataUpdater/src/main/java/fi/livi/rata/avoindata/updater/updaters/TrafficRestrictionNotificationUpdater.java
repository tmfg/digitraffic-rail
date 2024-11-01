package fi.livi.rata.avoindata.updater.updaters;

import org.apache.commons.lang3.ObjectUtils;
import org.locationtech.jts.geom.GeometryCollection;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.ruma.LocalRumaNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.LocalTrafficRestrictionNotificationService;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteRumaNotificationStatus;
import fi.livi.rata.avoindata.updater.service.ruma.RemoteTrafficRestrictionNotificationService;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

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
        if (ObjectUtils.isEmpty(liikeInterfaceUrl)) {
            return;
        }

        final RemoteRumaNotificationStatus[] statusesResp = remoteTrafficRestrictionNotificationService.getStatuses();
        log.info("Received {} traffic restriction notification statuses", statusesResp.length);

        if (statusesResp != null && statusesResp.length > 0) {
            final Map<String, Long> statuses = Arrays.stream(statusesResp)
                    .collect(Collectors.toMap(RemoteRumaNotificationStatus::getId, RemoteRumaNotificationStatus::getVersion));
            log.info("Received {} traffic restriction notification statuses", statuses.size());
            final List<LocalRumaNotificationStatus> localTrafficRestrictionNotifications = localTrafficRestrictionNotificationService.getLocalTrafficRestrictionNotifications(statuses.keySet());
            addNewTrafficRestrictionNotifications(statuses, localTrafficRestrictionNotifications);
            updateTrafficRestrictionNotifications(statuses, localTrafficRestrictionNotifications);
            lastUpdateService.update(LastUpdateService.LastUpdatedType.TRAFFIC_RESTRICTION_NOTIFICATIONS);
        } else {
            log.error("Error retrieving traffic restriction notification statuses or received empty response");
        }
 
    }

    private void addNewTrafficRestrictionNotifications(final Map<String, Long> statuses, final List<LocalRumaNotificationStatus> localTrafficRestrictionNotifications) {
        final Collection<String> newTrafficRestrictionNotifications = CollectionUtils.disjunction(localTrafficRestrictionNotifications.stream().map(LocalRumaNotificationStatus::getId).collect(Collectors.toSet()), statuses.keySet());

        for (final Map.Entry<String, Long> e : statuses.entrySet()) {
            if (newTrafficRestrictionNotifications.contains(e.getKey())) {
                updateTrafficRestrictionNotification(e.getKey(), new TreeSet<>(LongStream.rangeClosed(1, e.getValue()).boxed().collect(Collectors.toList())));
            }
        }
        log.info("Added {} new traffic restriction notifications", newTrafficRestrictionNotifications.size());
    }

    private void updateTrafficRestrictionNotifications(final Map<String, Long> statuses, final List<LocalRumaNotificationStatus> localTrafficRestrictionNotifications) {
        final int updatedCount = localTrafficRestrictionNotifications.stream()
                .map(localTrafficRestrictionNotification -> updateNotificationVersions(localTrafficRestrictionNotification, statuses))
                .filter(Boolean::booleanValue)
                .mapToInt(x -> 1)
                .sum();
        log.info("Updated {} traffic restriction notifications", updatedCount);
    }

    private boolean updateNotificationVersions(final LocalRumaNotificationStatus localTrafficRestrictionNotification, final Map<String, Long> statuses) {
        if (!statuses.containsKey(localTrafficRestrictionNotification.id)) {
            return false;
        }
       final long remoteVersion = statuses.get(localTrafficRestrictionNotification.id);
        final SortedSet<Long> versions = new TreeSet<>();
        if (remoteVersion > localTrafficRestrictionNotification.maxVersion) {
            LongStream.rangeClosed(localTrafficRestrictionNotification.maxVersion + 1, remoteVersion).forEach(versions::add);
        }
        if (!versions.isEmpty()) {
            return updateTrafficRestrictionNotification(localTrafficRestrictionNotification.id, versions);
        }
        return false;
    }

    private boolean updateTrafficRestrictionNotification(final String id, final SortedSet<Long> versions) {
        log.info("Updating TrafficRestrictionNotification {}, required versions {}", id, versions);
        final List<TrafficRestrictionNotification> trackWorkNotificationVersions = remoteTrafficRestrictionNotificationService.getTrafficRestrictionNotificationVersions(id, versions.stream().mapToLong(Long::longValue));
        log.info("Got {} versions for TrafficRestrictionNotification {}", trackWorkNotificationVersions.size(), id);

        final List<TrafficRestrictionNotification> versionsToBeSaved = trackWorkNotificationVersions.stream()
                .filter(trn -> {
                    if (trn.state == TrafficRestrictionNotificationState.DRAFT) {
                        return false;
                    }

                    if (trn.state == TrafficRestrictionNotificationState.FINISHED) {
                        final boolean previousVersionIsDraft = previousVersionIsDraft(trn.id, trackWorkNotificationVersions);
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

                        for (final IdentifierRange ir : rl.identifierRanges) {
                            if (ir.elementId != null && RumaUpdaterUtil.elementIsVaihde(ir.elementId)) {
                                ir.locationMap = wgs84ConversionService.liviToWgs84Jts(RumaUpdaterUtil.getPointFromGeometryCollection((GeometryCollection) ir.locationMap, trn.id.id));
                            } else {
                                ir.locationMap = wgs84ConversionService.liviToWgs84Jts(ir.locationMap);
                            }
                            ir.locationSchema = wgs84ConversionService.liviToWgs84Jts(ir.locationSchema);
                        }
                    });
                }).collect(Collectors.toList());

        if (!versionsToBeSaved.isEmpty()) {
            localTrafficRestrictionNotificationService.saveAll(versionsToBeSaved);
            return true;
        }
        return false;
    }

    private boolean previousVersionIsDraft(final TrafficRestrictionNotification.TrafficRestrictionNotificationId id, final List<TrafficRestrictionNotification> trackWorkNotificationVersions) {
        final long previousVersion = id.version - 1;

        for (final TrafficRestrictionNotification trn : trackWorkNotificationVersions) {
            if (trn.id.id.equals(id.id) && trn.id.version.equals(previousVersion) && trn.state == TrafficRestrictionNotificationState.DRAFT) {
                return true;
            }
        }

        return false;
    }

}
