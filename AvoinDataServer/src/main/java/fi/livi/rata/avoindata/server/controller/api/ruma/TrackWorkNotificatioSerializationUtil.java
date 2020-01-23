package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryUtils;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TrackWorkNotificatioSerializationUtil {

    public static SpatialTrackWorkNotificationDto toTwnDto(TrackWorkNotification twn) {
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
                GeometryUtils.fromJtsGeometry(twn.locationMap),
                GeometryUtils.fromJtsGeometry(twn.locationSchema),
                twn.trackWorkParts.stream().map(twp ->
                        new TrackWorkPartDto(twp.partIndex,
                                twp.startDay,
                                twp.permissionMinimumDuration,
                                twp.containsFireWork,
                                twp.plannedWorkingGap,
                                twp.advanceNotifications,
                                twp.locations.stream().map(r ->
                                        new SpatialRumaLocationDto(r.locationType,
                                                r.operatingPointId,
                                                r.sectionBetweenOperatingPointsId,
                                                r.identifierRanges.stream().map(ir ->
                                                        new SpatialIdentifierRangeDto(ir.elementId,
                                                                ir.elementPairId1,
                                                                ir.elementPairId2,
                                                                ir.elementRanges.stream().map(er ->
                                                                        new ElementRangeDto(er.elementId1,
                                                                                er.elementId2,
                                                                                er.trackKilometerRange,
                                                                                er.trackIds,
                                                                                er.specifiers)
                                                                ).collect(Collectors.toSet()),
                                                                GeometryUtils.fromJtsGeometry(ir.locationMap),
                                                                GeometryUtils.fromJtsGeometry(ir.locationSchema))
                                                ).collect(Collectors.toSet()),
                                                r.locationMap != null ? GeometryUtils.fromJtsGeometry(r.locationMap) : null,
                                                r.locationSchema != null ? GeometryUtils.fromJtsGeometry(r.locationSchema) : null)
                                ).collect(Collectors.toSet()))
                ).collect(Collectors.toList()));
    }

    public static Stream<Feature> toFeatures(TrackWorkNotification twn) {
        List<Feature> features = new ArrayList<>();

        features.add(new Feature(twn.locationMap, new TrackWorkNotificationDto(twn.id,
                twn.state,
                twn.organization,
                twn.created,
                twn.modified,
                twn.trafficSafetyPlan,
                twn.speedLimitPlan,
                twn.speedLimitRemovalPlan,
                twn.electricitySafetyPlan,
                twn.personInChargePlan)));

        for (TrackWorkPart twp : twn.trackWorkParts) {

            for (RumaLocation rl : twp.locations) {
                if (rl.locationMap != null) {
                    features.add(new Feature(rl.locationMap, new RumaLocationDto(rl.locationType,
                            rl.operatingPointId,
                            rl.sectionBetweenOperatingPointsId)));
                }

                for (IdentifierRange ir : rl.identifierRanges) {
                    features.add(new Feature(ir.locationMap, new IdentifierRangeDto(ir.elementId,
                            ir.elementPairId1,
                            ir.elementPairId2,
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

        return features.stream();
    }

}
