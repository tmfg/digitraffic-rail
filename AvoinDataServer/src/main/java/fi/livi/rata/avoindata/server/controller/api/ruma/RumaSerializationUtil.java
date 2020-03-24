package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.vividsolutions.jts.geom.Geometry;
import fi.livi.rata.avoindata.common.domain.spatial.GeometryUtils;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RumaSerializationUtil {

    public static SpatialTrackWorkNotificationDto toTwnDto(final TrackWorkNotification twn, final boolean schema) {
        return new SpatialTrackWorkNotificationDto(twn.id,
                twn.state,
                twn.organization,
                twn.created,
                twn.modified,
                twn.trafficSafetyPlan,
                twn.speedLimitPlan,
                twn.speedLimitRemovalPlan,
                twn.electricitySafetyPlan,
                twn.personInChargePlan,
                GeometryUtils.fromJtsGeometry(schema ? twn.locationSchema : twn.locationMap),
                twn.trackWorkParts.stream().map(twp ->
                        new TrackWorkPartDto(
                                twp.partIndex,
                                twp.startDay,
                                twp.permissionMinimumDuration,
                                twp.containsFireWork,
                                twp.plannedWorkingGap,
                                twp.advanceNotifications,
                                twp.locations.stream().map(r -> toRumaLocationDto(twn.id.id, twp.partIndex, r, schema)).collect(Collectors.toSet()))
                ).collect(Collectors.toList()));
    }

    public static SpatialTrafficRestrictionNotificationDto toTrnDto(final TrafficRestrictionNotification trn, final boolean schema) {
        return new SpatialTrafficRestrictionNotificationDto(trn.id,
                trn.state,
                trn.organization,
                trn.created,
                trn.modified,
                trn.limitation,
                trn.twnId,
                trn.axleWeightMax,
                trn.startDate,
                trn.endDate,
                trn.finished,
                GeometryUtils.fromJtsGeometry(schema ? trn.locationSchema : trn.locationMap),
                trn.locations.stream().map(r -> toRumaLocationDto(trn.id.id, null, r, schema)).collect(Collectors.toSet()));
    }

    protected static SpatialRumaLocationDto toRumaLocationDto(
            final String notificationId,
            final Long workPartIndex,
            final RumaLocation r,
            final boolean schema) {
        final Geometry locationGeom = schema ? r.locationSchema : r.locationMap;
        return new SpatialRumaLocationDto(
                notificationId,
                workPartIndex,
                r.locationType,
                r.operatingPointId,
                r.sectionBetweenOperatingPointsId,
                r.identifierRanges.stream().map(ir -> toIdentifierRangeDto(notificationId, ir, schema)).collect(Collectors.toSet()),
                locationGeom != null ? GeometryUtils.fromJtsGeometry(locationGeom) : null);
    }

    protected static SpatialIdentifierRangeDto toIdentifierRangeDto(final String notificationId, final IdentifierRange ir, final boolean schema) {
        return new SpatialIdentifierRangeDto(
                notificationId,
                ir.elementId,
                ir.elementPairId1,
                ir.elementPairId2,
                ir.speedLimit,
                ir.elementRanges.stream().map(er ->
                        new ElementRangeDto(
                                er.elementId1,
                                er.elementId2,
                                er.trackKilometerRange,
                                er.trackIds,
                                er.specifiers)
                ).collect(Collectors.toSet()),
                GeometryUtils.fromJtsGeometry(schema ? ir.locationSchema : ir.locationMap));
    }

    public static Stream<Feature> toTwnFeatures(final TrackWorkNotification twn, final boolean schema) {
        final List<Feature> features = new ArrayList<>();

        features.add(new Feature(schema ? twn.locationSchema : twn.locationMap, new TrackWorkNotificationDto(twn.id,
                twn.state,
                twn.organization,
                twn.created,
                twn.modified,
                twn.trafficSafetyPlan,
                twn.speedLimitPlan,
                twn.speedLimitRemovalPlan,
                twn.electricitySafetyPlan,
                twn.personInChargePlan)));

        for (final TrackWorkPart twp : twn.trackWorkParts) {
            final List<Feature> locationFeatures = extractLocationFeatures(twn.id.id, twp.partIndex, twp.locations, schema);
            features.addAll(locationFeatures);
        }

        return features.stream();
    }

    public static Stream<Feature> toTrnFeatures(final TrafficRestrictionNotification trn, final boolean schema) {
        final List<Feature> features = new ArrayList<>();

        features.add(new Feature(schema ? trn.locationSchema : trn.locationMap, new TrafficRestrictionNotificationDto(trn.id,
                trn.state,
                trn.organization,
                trn.created,
                trn.modified,
                trn.limitation,
                trn.twnId,
                trn.axleWeightMax,
                trn.startDate,
                trn.endDate,
                trn.finished)));

        features.addAll(extractLocationFeatures(trn.id.id, null, trn.locations, schema));

        return features.stream();
    }

    private static List<Feature> extractLocationFeatures(
            final String notificationIdentifier,
            final Long trackWortPartIndex,
            final Set<RumaLocation> locations,
            final Boolean schema) {
        final List<Feature> features = new ArrayList<>();

        for (final RumaLocation rl : locations) {

            if (rl.identifierRanges.isEmpty()) {
                final Geometry locationGeom = schema ? rl.locationSchema : rl.locationMap;
                if (locationGeom != null) {
                    features.add(new Feature(
                            locationGeom,
                            new RumaLocationDto(
                                    notificationIdentifier,
                                    trackWortPartIndex,
                                    rl.locationType,
                                    rl.operatingPointId,
                                    rl.sectionBetweenOperatingPointsId)));
                }
            } else {
                for (final IdentifierRange ir : rl.identifierRanges) {
                    features.add(new Feature(schema ? ir.locationSchema : ir.locationMap,
                            new IdentifierRangeDto(
                                    notificationIdentifier,
                                    ir.elementId,
                                    ir.elementPairId1,
                                    ir.elementPairId2,
                                    ir.speedLimit,
                                    ir.elementRanges.stream().map(er ->
                                            new ElementRangeDto(er.elementId1,
                                                    er.elementId2,
                                                    er.trackKilometerRange,
                                                    er.trackIds,
                                                    er.specifiers)
                                    ).collect(Collectors.toSet()))));
                }

            }

        }

        return features;
    }

}
