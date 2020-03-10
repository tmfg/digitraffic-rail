package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryDto;
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
            final Set<ElementRangeDto> elementRanges,
            final GeometryDto<?> location)
    {
        super(trackWorkNotificationId, elementId, elementPairId1, elementPairId2, elementRanges);
        this.location = location;
    }
}
