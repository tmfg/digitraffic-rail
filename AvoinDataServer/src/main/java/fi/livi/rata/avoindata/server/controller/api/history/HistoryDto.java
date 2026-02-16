package fi.livi.rata.avoindata.server.controller.api.history;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name ="History", title = "History")
public class HistoryDto {
    @Schema(description = "History version number", requiredMode = Schema.RequiredMode.REQUIRED)
    public int version;

    @Schema(description = "History train identifier", requiredMode = Schema.RequiredMode.REQUIRED)
    public VersionDto id;

    @Schema(description = "Content", requiredMode = Schema.RequiredMode.REQUIRED)
    public Object json;
}
