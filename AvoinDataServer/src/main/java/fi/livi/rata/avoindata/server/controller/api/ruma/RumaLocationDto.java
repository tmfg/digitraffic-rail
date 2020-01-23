package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.annotations.ApiModelProperty;

public class RumaLocationDto {

    @ApiModelProperty("Track work notification identifier")
    @JsonView(RumaJsonViews.GeoJsonView.class)
    public final long trackWorkNotificationId;

    @ApiModelProperty("Type")
    public final LocationType locationType;

    @ApiModelProperty("Identifier of operating point")
    public final String operatingPointId;

    @ApiModelProperty("Identifier of section between operating points")
    public final String sectionBetweenOperatingPointsId;

    public RumaLocationDto(
            final long trackWorkNotificationId,
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId)
    {
        this.trackWorkNotificationId = trackWorkNotificationId;
        this.locationType = locationType;
        this.operatingPointId = operatingPointId;
        this.sectionBetweenOperatingPointsId = sectionBetweenOperatingPointsId;
    }
}
