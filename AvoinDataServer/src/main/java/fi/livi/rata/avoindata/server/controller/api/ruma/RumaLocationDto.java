package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.fasterxml.jackson.annotation.JsonView;

import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name ="RumaLocation", title = "RumaLocation", description = "Location of work, either an operating point or a section between operating points")
public class RumaLocationDto {

    @Schema(description = "Notification identifier", required = true)
    @JsonView(RumaJsonViews.GeoJsonView.class)
    public final String notificationId;

    @Schema(description = "Track work part index", required = true)
    public final Long workPartIndex;

    @Schema(description = "Type", required = true)
    public final LocationType locationType;

    @Schema(description = "Identifier of operating point, required if section is not present")
    public final String operatingPointId;

    @Schema(description = "Identifier of section between operating points, required if operating point is not present")
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
