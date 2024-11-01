package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Describes a restriction affecting the use of a railway infrastructure part")
public class TrafficRestrictionNotificationDto {

    public final TrafficRestrictionNotification.TrafficRestrictionNotificationId id;

    @Schema(description = "State", requiredMode = Schema.RequiredMode.REQUIRED)
    public final TrafficRestrictionNotificationState state;

    @Schema(description = "Which organization created this notification", requiredMode = Schema.RequiredMode.REQUIRED)
    public final String organization;

    @Schema(description = "When this notification was created", requiredMode = Schema.RequiredMode.REQUIRED)
    public final ZonedDateTime created;

    @Schema(description = "When this notification last modified")
    public final ZonedDateTime modified;

    @Schema(description = "Limitation type", requiredMode = Schema.RequiredMode.REQUIRED)
    public final TrafficRestrictionType limitation;

    @Schema(description = "Track work notification identifier")
    public final String trackWorkNotificationId;

    @Schema(description = "Max axle weight, required if limitation type is max axle weight")
    public final Double axleWeightMax;

    @Schema(description = "Start datetime", requiredMode = Schema.RequiredMode.REQUIRED)
    public final ZonedDateTime startDate;

    @Schema(description = "End datetime")
    public final ZonedDateTime endDate;

    @Schema(description = "Finished datetime, required if state is finished")
    public final ZonedDateTime finished;

    public TrafficRestrictionNotificationDto(
            final TrafficRestrictionNotification.TrafficRestrictionNotificationId id,
            final TrafficRestrictionNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final TrafficRestrictionType limitation,
            final String trackWorkNotificationId,
            final Double axleWeightMax,
            final ZonedDateTime startDate,
            final ZonedDateTime endDate,
            final ZonedDateTime finished)
    {
        this.id = id;
        this.state = state;
        this.organization = organization;
        this.created = created;
        this.modified = modified;
        this.limitation = limitation;
        this.trackWorkNotificationId = trackWorkNotificationId;
        this.axleWeightMax = axleWeightMax;
        this.startDate = startDate;
        this.endDate = endDate;
        this.finished = finished;
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
