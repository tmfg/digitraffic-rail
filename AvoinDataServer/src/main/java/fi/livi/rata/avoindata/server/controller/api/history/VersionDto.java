package fi.livi.rata.avoindata.server.controller.api.history;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Schema(name = "Version")
public class VersionDto {
    @Schema(description = "Train number", requiredMode = Schema.RequiredMode.REQUIRED)
    public int trainNumber;

    @Schema(description = "Departure date", requiredMode = Schema.RequiredMode.REQUIRED)
    public LocalDate departureDate;

    @Schema(description = "Date when this version was fetched", requiredMode = Schema.RequiredMode.REQUIRED)
    public ZonedDateTime fetchDate;
}
