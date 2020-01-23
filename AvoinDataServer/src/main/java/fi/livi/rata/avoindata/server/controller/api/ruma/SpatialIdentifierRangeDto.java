package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.Geometry;
import io.swagger.annotations.ApiModelProperty;

import java.util.Set;

public class SpatialIdentifierRangeDto extends IdentifierRangeDto {

    @ApiModelProperty("Location on map")
    public final Geometry<?> locationMap;

    @ApiModelProperty("Location in schema")
    public final Geometry<?> locationSchema;

    @ApiModelProperty("Element ranges")
    public final Set<ElementRangeDto> elementRanges;

    public SpatialIdentifierRangeDto(
            final String elementId,
            final String elementPairId1,
            final String elementPairId2,
            final Set<ElementRangeDto> elementRanges,
            final Geometry<?> locationMap,
            final Geometry<?> locationSchema)
    {
        super(elementId, elementPairId1, elementPairId2);
        this.locationMap = locationMap;
        this.locationSchema = locationSchema;
        this.elementRanges = elementRanges;
    }
}
