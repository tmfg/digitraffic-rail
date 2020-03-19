package fi.livi.rata.avoindata.server.controller.api.ruma;

import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class TrackWorkNotificationWithVersions {

    @ApiModelProperty("Track work notification id")
    public final String id;

    @ApiModelProperty("Track work notification versions")
    public final List<SpatialTrackWorkNotificationDto> versions;

    public TrackWorkNotificationWithVersions(String id, final List<SpatialTrackWorkNotificationDto> versions) {
        this.id = id;
        this.versions = versions;
    }

}
