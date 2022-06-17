package fi.livi.rata.avoindata.server.controller.api.ruma;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Two consecutive elements in an identifier range")
public class ElementRangeDto {

    @ApiModelProperty(value = "Identifier of element 1", required = true)
    public final String elementId1;

    @ApiModelProperty(value = "Identifier of element 2", required = true)
    public final String elementId2;

    @ApiModelProperty(value = "Track kilometer range, required if notification type is traffic restriction, e.g. (006) 754+0273 > 764+0771")
    public final String trackKilometerRange;

    @ApiModelProperty(value = "Track identifiers", required = true)
    public final List<String> trackIds;

    @ApiModelProperty(value = "Specify a more detailed work area (track element)")
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
