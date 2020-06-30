package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.time.ZonedDateTime;

@ApiModel(description = "Describes planned, in-progress or finished track work")
public class TrackWorkNotificationDto {

    public TrackWorkNotification.TrackWorkNotificationId id;

    @ApiModelProperty(value = "State", required = true)
    public final TrackWorkNotificationState state;

    @ApiModelProperty(value = "Which organization created this notification", required = true)
    public final String organization;

    @ApiModelProperty(value = "When this notification was created", required = true)
    public final ZonedDateTime created;

    @ApiModelProperty(value = "When this notification last modified")
    public final ZonedDateTime modified;

    @ApiModelProperty(value = "Does the notification contain a traffic safety plan", required = true)
    public final Boolean trafficSafetyPlan;

    @ApiModelProperty(value = "Does the notification contain a speed limit removal plan", required = true)
    public final Boolean speedLimitRemovalPlan;

    @ApiModelProperty(value = "Does the notification contain a electricity safety plan", required = true)
    public final Boolean electricitySafetyPlan;

    @ApiModelProperty(value = "Does the notification contain a speed limit plan", required = true)
    public final Boolean speedLimitPlan;

    @ApiModelProperty(value = "Does the notification contain a plan for persons in charge", required = true)
    public final Boolean personInChargePlan;

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
            final Boolean personInChargePlan)
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
    }

    @ApiModelProperty(value = "Id", required = true)
    public String getId() {
        return id.id;
    }

    @ApiModelProperty(value = "Version", required = true)
    public Long getVersion() {
        return id.version;
    }

}
