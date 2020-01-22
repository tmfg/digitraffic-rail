package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.Geometry;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import io.swagger.annotations.ApiModelProperty;
import java.time.ZonedDateTime;
import java.util.List;

public class TrackWorkNotificationDto {

    public TrackWorkNotification.TrackWorkNotificationId id;

    @ApiModelProperty("State")
    public final TrackWorkNotificationState state;

    @ApiModelProperty("Which organization created this notification")
    public final String organization;

    @ApiModelProperty("When this notification was created")
    public final ZonedDateTime created;

    @ApiModelProperty("When this notification last modified")
    public final ZonedDateTime modified;

    @ApiModelProperty("Does the notification contain a traffic safety plan")
    public final Boolean trafficSafetyPlan;

    @ApiModelProperty("Does the notification contain a speed limit removal plan")
    public final Boolean speedLimitRemovalPlan;

    @ApiModelProperty("Does the notification contain a electricity safety plan")
    public final Boolean electricitySafetyPlan;

    @ApiModelProperty("Does the notification contain a speed limit plan")
    public final Boolean speedLimitPlan;

    @ApiModelProperty("Does the notification contain a plan for persons in charge")
    public final Boolean personInChargePlan;

    @ApiModelProperty("Approximate location on map")
    public final Geometry<?> locationMap;

    @ApiModelProperty("Approximate location in schema")
    public final Geometry<?> locationSchema;

    @ApiModelProperty("Work parts")
    public final List<TrackWorkPartDto> workParts;

    public TrackWorkNotificationDto(
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
            final Geometry<?> locationMap,
            final Geometry<?> locationSchema,
            final List<TrackWorkPartDto> workParts)
    {
        this.id = id;
        this.state = state;
        this.organization = organization;
        this.created = created;
        this.modified = modified;
        this.trafficSafetyPlan = trafficSafetyPlan;
        this.speedLimitPlan = speedLimitPlan;
        this.speedLimitRemovalPlan = speedLimitRemovalPlan;
        this.electricitySafetyPlan = electricitySafetyPlan;
        this.personInChargePlan = personInChargePlan;
        this.locationMap = locationMap;
        this.locationSchema = locationSchema;
        this.workParts = workParts;
    }

    @ApiModelProperty("Id")
    public Long getId() {
        return id.id;
    }

    @ApiModelProperty("Version")
    public Long getVersion() {
        return id.version;
    }

}
