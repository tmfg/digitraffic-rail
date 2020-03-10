package fi.livi.rata.avoindata.common.dao;

import io.swagger.annotations.ApiModelProperty;

import java.time.ZonedDateTime;

public interface RumaNotificationIdAndVersion {

    @ApiModelProperty("Id")
    String getId();

    @ApiModelProperty("Version")
    Long getVersion();

    @ApiModelProperty("Last modified")
    ZonedDateTime getModified();
}

