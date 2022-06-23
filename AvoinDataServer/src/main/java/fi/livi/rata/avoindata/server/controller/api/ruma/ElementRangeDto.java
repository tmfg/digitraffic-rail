package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name ="ElementRange", description = "Two consecutive elements in an identifier range")
public class ElementRangeDto {

    @Schema(description = "Identifier of element 1", required = true)
    public final String elementId1;

    @Schema(description = "Identifier of element 2", required = true)
    public final String elementId2;

    @Schema(description = "Track kilometer range, required if notification type is traffic restriction, e.g. (006) 754+0273 > 764+0771")
    public final String trackKilometerRange;

    @Schema(description = "Track identifiers", required = true)
    public final List<String> trackIds;

    @Schema(description = "Specify a more detailed work area (track element)")
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
