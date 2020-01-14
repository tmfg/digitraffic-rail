package fi.livi.rata.avoindata.common.dao.trackwork;

import io.swagger.annotations.ApiModelProperty;

public interface TrackWorkNotificationIdAndVersion {

    @ApiModelProperty("Track work notification id")
    Integer getId();

    @ApiModelProperty("Track work notification version")
    Integer getVersion();

}

