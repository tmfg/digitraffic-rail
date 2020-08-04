package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.time.ZonedDateTime;
import java.util.List;

@ApiModel(description = "Describes a restriction affecting the use of a railway infrastructure part")
public class TrafficRestrictionNotificationDto {

    public final TrafficRestrictionNotification.TrafficRestrictionNotificationId id;

    @ApiModelProperty(value = "State", required = true)
    public final TrafficRestrictionNotificationState state;

    @ApiModelProperty(value = "Which organization created this notification", required = true)
    public final String organization;

    @ApiModelProperty(value = "When this notification was created", required = true)
    public final ZonedDateTime created;

    @ApiModelProperty(value = "When this notification last modified")
    public final ZonedDateTime modified;

    @ApiModelProperty(value = "Limitation type", required = true)
    public final TrafficRestrictionType limitation;

    @ApiModelProperty(value = "Track work notification identifier")
    public final String trackWorkNotificationId;

    @ApiModelProperty(value = "Max axle weight, required if limitation type is max axle weight")
    public final Double axleWeightMax;

    @ApiModelProperty(value = "Start datetime", required = true)
    public final ZonedDateTime startDate;

    @ApiModelProperty(value = "End datetime")
    public final ZonedDateTime endDate;

    @ApiModelProperty(value = "Finished datetime, required if state is finished")
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

    @ApiModelProperty(value = "Id", required = true)
    public String getId() {
        return id.id;
    }

    @ApiModelProperty(value = "Version", required = true)
    public Long getVersion() {
        return id.version;
    }

}
