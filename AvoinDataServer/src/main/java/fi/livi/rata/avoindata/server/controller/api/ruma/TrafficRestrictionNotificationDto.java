package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import io.swagger.annotations.ApiModelProperty;

import java.time.ZonedDateTime;
import java.util.List;

public class TrafficRestrictionNotificationDto {

    public final TrafficRestrictionNotification.TrafficRestrictionNotificationId id;

    @ApiModelProperty("State")
    public final TrafficRestrictionNotificationState state;

    @ApiModelProperty("Which organization created this notification")
    public final String organization;

    @ApiModelProperty("When this notification was created")
    public final ZonedDateTime created;

    @ApiModelProperty("When this notification last modified")
    public final ZonedDateTime modified;

    @ApiModelProperty("Limitation type")
    public final TrafficRestrictionType limitation;

    @ApiModelProperty("Track work notification identifier")
    public final String twnId;

    @ApiModelProperty("Max axle weight")
    public final Double axleWeightMax;

    @ApiModelProperty("Start datetime")
    public final ZonedDateTime startDate;

    @ApiModelProperty("End datetime")
    public final ZonedDateTime endDate;

    @ApiModelProperty("Finished datetime")
    public final ZonedDateTime finished;

    public TrafficRestrictionNotificationDto(
            final TrafficRestrictionNotification.TrafficRestrictionNotificationId id,
            final TrafficRestrictionNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final TrafficRestrictionType limitation,
            final String twnId,
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
        this.twnId = twnId;
        this.axleWeightMax = axleWeightMax;
        this.startDate = startDate;
        this.endDate = endDate;
        this.finished = finished;
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
