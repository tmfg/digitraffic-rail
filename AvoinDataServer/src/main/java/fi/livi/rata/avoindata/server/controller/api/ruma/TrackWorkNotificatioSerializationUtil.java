package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.vividsolutions.jts.geom.Geometry;
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

    public static SpatialTrackWorkNotificationDto toTwnDto(TrackWorkNotification twn, boolean schema) {
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
                                twp.locations.stream().map(r -> {
                                    Geometry locationGeom = schema ? r.locationSchema : r.locationMap;
                                    return new SpatialRumaLocationDto(
                                            twn.id.id,
                                            r.locationType,
                                            r.operatingPointId,
                                            r.sectionBetweenOperatingPointsId,
                                            r.identifierRanges.stream().map(ir ->
                                                    new SpatialIdentifierRangeDto(
                                                            twn.id.id,
                                                            ir.elementId,
                                                            ir.elementPairId1,
                                                            ir.elementPairId2,
                                                            ir.elementRanges.stream().map(er ->
                                                                    new ElementRangeDto(
                                                                            er.elementId1,
                                                                            er.elementId2,
                                                                            er.trackKilometerRange,
                                                                            er.trackIds,
                                                                            er.specifiers)
                                                            ).collect(Collectors.toSet()),
                                                            GeometryUtils.fromJtsGeometry(schema ? ir.locationSchema : ir.locationMap))
                                            ).collect(Collectors.toSet()),
                                            locationGeom != null ? GeometryUtils.fromJtsGeometry(locationGeom) : null);
                                        }
                                ).collect(Collectors.toSet()))
                ).collect(Collectors.toList()));
    }

    public static Stream<Feature> toFeatures(TrackWorkNotification twn, boolean schema) {
        List<Feature> features = new ArrayList<>();

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

        for (TrackWorkPart twp : twn.trackWorkParts) {

            for (RumaLocation rl : twp.locations) {
                Geometry locationGeom = schema ? rl.locationSchema : rl.locationMap;
                if (locationGeom != null) {
                    features.add(new Feature(
                            locationGeom,
                            new RumaLocationDto(
                                    twn.id.id,
                                    rl.locationType,
                                    rl.operatingPointId,
                                    rl.sectionBetweenOperatingPointsId)));
                }

                for (IdentifierRange ir : rl.identifierRanges) {
                    features.add(new Feature(schema ? ir.locationSchema : ir.locationMap,
                            new IdentifierRangeDto(
                                    twn.id.id,
                                    ir.elementId,
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
