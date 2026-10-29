package fi.livi.rata.avoindata.common.domain.lastupdated;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response model of the updater's last-updated endpoint. Shared so that the public API description
 * is generated from the same class that the updater returns.
 */
public record UpToDateInformation(
        @Schema(description = "When the data type was last updated")
        Instant lastUpdated,
        @Schema(type = "string", example = "PT5M",
                description = "How old the data can be before it is considered outdated (ISO 8601 duration)")
        Duration alarmLimit,
        @Schema(type = "string", example = "PT1.2S", description = "Time since the last update (ISO 8601 duration)")
        Duration durationSinceLastUpdate) {

    @JsonProperty("isUpToDate")
    @Schema(description = "False when the time since the last update is longer than the alarm limit")
    public boolean isUpToDate() {
        return !alarmLimit.minus(durationSinceLastUpdate).isNegative();
    }
}
