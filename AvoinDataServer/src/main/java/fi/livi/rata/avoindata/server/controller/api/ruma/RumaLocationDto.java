package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.annotations.ApiModelProperty;

public class RumaLocationDto {

    @ApiModelProperty("Notification identifier")
    @JsonView(RumaJsonViews.GeoJsonView.class)
    public final String notificationId;

    @ApiModelProperty("Track work part index")
    public final Long workPartIndex;

    @ApiModelProperty("Type")
    public final LocationType locationType;

    @ApiModelProperty("Identifier of operating point")
    public final String operatingPointId;

    @ApiModelProperty("Identifier of section between operating points")
    public final String sectionBetweenOperatingPointsId;

    public RumaLocationDto(
            final String notificationId,
            final Long workPartIndex,
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId)
    {
        this.notificationId = notificationId;
        this.workPartIndex = workPartIndex;
        this.locationType = locationType;
        this.operatingPointId = operatingPointId;
        this.sectionBetweenOperatingPointsId = sectionBetweenOperatingPointsId;
    }
}
