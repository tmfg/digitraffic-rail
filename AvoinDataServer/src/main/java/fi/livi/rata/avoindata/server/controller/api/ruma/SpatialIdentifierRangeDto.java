package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryDto;
import fi.livi.rata.avoindata.common.domain.trackwork.SpeedLimit;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class SpatialIdentifierRangeDto extends IdentifierRangeDto {

    @ApiModelProperty("Location")
    public final GeometryDto<?> location;

    public SpatialIdentifierRangeDto(
            final String trackWorkNotificationId,
            final String elementId,
            final String elementPairId1,
            final String elementPairId2,
            final SpeedLimit speedLimit,
            final Set<ElementRangeDto> elementRanges,
            final GeometryDto<?> location)
    {
        super(trackWorkNotificationId, elementId, elementPairId1, elementPairId2, speedLimit, elementRanges);
        this.location = location;
    }
}
