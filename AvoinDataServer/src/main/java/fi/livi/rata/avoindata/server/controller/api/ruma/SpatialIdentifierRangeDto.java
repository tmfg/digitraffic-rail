package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.util.Set;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryDto;
import fi.livi.rata.avoindata.common.domain.trackwork.SpeedLimit;
import io.swagger.v3.oas.annotations.media.Schema;

public class SpatialIdentifierRangeDto extends IdentifierRangeDto {

    @Schema(description = "Location")
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
