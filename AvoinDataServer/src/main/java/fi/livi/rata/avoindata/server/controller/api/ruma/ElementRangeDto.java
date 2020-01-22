package fi.livi.rata.avoindata.server.controller.api.ruma;

import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class ElementRangeDto {

    @ApiModelProperty("Identifier of element 1")
    public final String elementId1;

    @ApiModelProperty("Identifier of element 2")
    public final String elementId2;

    @ApiModelProperty("Track kilometer range")
    public final String trackKilometerRange;

    @ApiModelProperty("Track identifiers")
    public final List<String> trackIds;

    @ApiModelProperty("Specifiers")
    public final List<String> specifiers;

    public ElementRangeDto(
            final String elementId1,
            final String elementId2,
            final String trackKilometerRange,
            final List<String> trackIds,
            final List<String> specifiers)
    {
        this.elementId1 = elementId1;
        this.elementId2 = elementId2;
        this.trackKilometerRange = trackKilometerRange;
        this.trackIds = trackIds;
        this.specifiers = specifiers;
    }
}
