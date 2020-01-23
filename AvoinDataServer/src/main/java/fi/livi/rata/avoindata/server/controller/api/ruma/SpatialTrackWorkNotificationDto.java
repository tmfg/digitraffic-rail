package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.Geometry;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import io.swagger.annotations.ApiModelProperty;

import java.time.ZonedDateTime;
import java.util.List;

public class SpatialTrackWorkNotificationDto extends TrackWorkNotificationDto {

    @ApiModelProperty("Approximate location")
    public final Geometry<?> location;

    @ApiModelProperty("Work parts")
    public final List<TrackWorkPartDto> workParts;

    public SpatialTrackWorkNotificationDto(
            final TrackWorkNotification.TrackWorkNotificationId id,
            final TrackWorkNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final Boolean trafficSafetyPlan,
            final Boolean speedLimitPlan,
            final Boolean speedLimitRemovalPlan,
            final Boolean electricitySafetyPlan,
            final Boolean personInChargePlan,
            final Geometry<?> location,
            final List<TrackWorkPartDto> workParts)
    {
        super(id, state, organization, created, modified, trafficSafetyPlan, speedLimitPlan, speedLimitRemovalPlan, electricitySafetyPlan, personInChargePlan);
        this.location = location;
        this.workParts = workParts;
    }

}
