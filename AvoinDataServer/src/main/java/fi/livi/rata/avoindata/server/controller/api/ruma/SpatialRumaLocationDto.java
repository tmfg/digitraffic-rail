package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.util.Set;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryDto;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;

public class SpatialRumaLocationDto extends RumaLocationDto {

    @Schema(description = "Location if no identifer ranges are present")
    public final GeometryDto<?> location;

    @Schema(description = "Identifier ranges")
    public final Set<IdentifierRangeDto> identifierRanges;

    public SpatialRumaLocationDto(
            final String notificationId,
            final Long workPartIndex,
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId,
            final Set<IdentifierRangeDto> identifierRanges,
            final GeometryDto<?> location)
    {
        super(notificationId, workPartIndex, locationType, operatingPointId, sectionBetweenOperatingPointsId);
        this.location = location;
        this.identifierRanges = identifierRanges;
    }
}
