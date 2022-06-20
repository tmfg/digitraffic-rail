package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.trackwork.SpeedLimit;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Place of work: between two track elements or a single track element")
public class IdentifierRangeDto {

    @Schema(description = "Notification identifier", required = true)
    @JsonView(RumaJsonViews.GeoJsonView.class)
    public final String notificationId;

    @Schema(description = "Identifier of element, required if element pair or ranges are not present")
    public final String elementId;

    @Schema(description = "Identifier of element 1 in element pair, required if element or ranges are not present")
    public final String elementPairId1;

    @Schema(description = "Identifier of element 2 in element pair, required if element or ranges are not present")
    public final String elementPairId2;

    @Schema(description = "Speed limit, required if notification type is traffic restriction and it's type if speed limit")
    public final SpeedLimit speedLimit;

    @Schema(description = "Element ranges, required if element or element pair is not present")
    public final Set<ElementRangeDto> elementRanges;

    public IdentifierRangeDto(
            final String notificationId,
            final String elementId,
            final String elementPairId1,
            final String elementPairId2,
            final SpeedLimit speedLimit,
            final Set<ElementRangeDto> elementRanges)
    {
        this.notificationId = notificationId;
        this.elementId = elementId;
        this.elementPairId1 = elementPairId1;
        this.elementPairId2 = elementPairId2;
        this.speedLimit = speedLimit;
        this.elementRanges = elementRanges;
    }
}
