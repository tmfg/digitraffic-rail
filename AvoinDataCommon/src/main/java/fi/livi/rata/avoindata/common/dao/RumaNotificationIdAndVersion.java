package fi.livi.rata.avoindata.common.dao;

import java.time.ZonedDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

public interface RumaNotificationIdAndVersion {

    @Schema(description = "Id")
    String getId();

    @Schema(description = "Version")
    Long getVersion();

    @Schema(description = "Last modified")
    ZonedDateTime getModified();
}

