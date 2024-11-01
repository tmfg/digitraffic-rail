package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TrackWorkNotification", title = "TrackWorkNotification", description = "Describes planned, in-progress or finished track work")
public class TrackWorkNotificationDto {

    public TrackWorkNotification.TrackWorkNotificationId id;

    @Schema(description = "State", requiredMode = Schema.RequiredMode.REQUIRED)
    public final TrackWorkNotificationState state;

    @Schema(description = "Which organization created this notification", requiredMode = Schema.RequiredMode.REQUIRED)
    public final String organization;

    @Schema(description = "When this notification was created", requiredMode = Schema.RequiredMode.REQUIRED)
    public final ZonedDateTime created;

    @Schema(description = "When this notification last modified")
    public final ZonedDateTime modified;

    @Schema(description = "Does the notification contain a traffic safety plan", requiredMode = Schema.RequiredMode.REQUIRED)
    public final Boolean trafficSafetyPlan;

    @Schema(description = "Does the notification contain a speed limit removal plan", requiredMode = Schema.RequiredMode.REQUIRED)
    public final Boolean speedLimitRemovalPlan;

    @Schema(description = "Does the notification contain a electricity safety plan", requiredMode = Schema.RequiredMode.REQUIRED)
    public final Boolean electricitySafetyPlan;

    @Schema(description = "Does the notification contain a speed limit plan", requiredMode = Schema.RequiredMode.REQUIRED)
    public final Boolean speedLimitPlan;

    @Schema(description = "Does the notification contain a plan for persons in charge", requiredMode = Schema.RequiredMode.REQUIRED)
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

    @Schema(description = "Id", requiredMode = Schema.RequiredMode.REQUIRED)
    public String getId() {
        return id.id;
    }

    @Schema(description = "Version", requiredMode = Schema.RequiredMode.REQUIRED)
    public Long getVersion() {
        return id.version;
    }

}
