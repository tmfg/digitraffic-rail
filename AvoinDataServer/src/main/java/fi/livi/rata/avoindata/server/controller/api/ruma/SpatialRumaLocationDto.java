package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryDto;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class SpatialRumaLocationDto extends RumaLocationDto {

    @ApiModelProperty("Location if no identifer ranges are present")
    public final GeometryDto<?> location;

    @ApiModelProperty("Identifier ranges")
    public final Set<IdentifierRangeDto> identifierRanges;

    public SpatialRumaLocationDto(
            final long trackWorkNotificationId,
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId,
            final Set<IdentifierRangeDto> identifierRanges,
            final GeometryDto<?> location)
    {
        super(trackWorkNotificationId, locationType, operatingPointId, sectionBetweenOperatingPointsId);
        this.location = location;
        this.identifierRanges = identifierRanges;
    }
}
