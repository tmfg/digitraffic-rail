package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class RumaLocationDto {

    @ApiModelProperty("Type")
    public final LocationType locationType;

    @ApiModelProperty("Identifier of operating point")
    public final String operatingPointId;

    @ApiModelProperty("Identifier of section between operating points")
    public final String sectionBetweenOperatingPointsId;

    @ApiModelProperty("Identifier ranges")
    public final Set<IdentifierRangeDto> identifierRanges;

    public RumaLocationDto(
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId,
            final Set<IdentifierRangeDto> identifierRanges)
    {
        this.locationType = locationType;
        this.operatingPointId = operatingPointId;
        this.sectionBetweenOperatingPointsId = sectionBetweenOperatingPointsId;
        this.identifierRanges = identifierRanges;
    }
}
