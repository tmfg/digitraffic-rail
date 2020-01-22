package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.Geometry;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class SpatialRumaLocationDto extends RumaLocationDto {

    @ApiModelProperty("Location on map if no identifer ranges are present")
    public final Geometry<?> locationMap;

    @ApiModelProperty("Location in schema if no identifer ranges are present")
    public final Geometry<?> locationSchema;

    public SpatialRumaLocationDto(
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId,
            final Set<IdentifierRangeDto> identifierRanges,
            final Geometry<?> locationMap,
            final Geometry<?> locationSchema)
    {
        super(locationType, operatingPointId, sectionBetweenOperatingPointsId, identifierRanges);
        this.locationMap = locationMap;
        this.locationSchema = locationSchema;
    }
}
