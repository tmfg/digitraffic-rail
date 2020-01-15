package fi.livi.rata.avoindata.common.dao.trackwork;

import io.swagger.annotations.ApiModelProperty;

import java.time.ZonedDateTime;

public interface TrackWorkNotificationIdAndVersion {

    @ApiModelProperty("Id")
    Long getId();

    @ApiModelProperty("Version")
    Long getVersion();

    @ApiModelProperty("Last modified")
    ZonedDateTime getModified();
}

