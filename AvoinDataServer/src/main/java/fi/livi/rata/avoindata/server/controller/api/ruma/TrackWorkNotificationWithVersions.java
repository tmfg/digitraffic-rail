package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class TrackWorkNotificationWithVersions {

    @ApiModelProperty("Track work notification id")
    public final int id;

    @ApiModelProperty("Track work notification versions")
    public final List<TrackWorkNotification> versions;

    public TrackWorkNotificationWithVersions(int id, final List<TrackWorkNotification> versions) {
        this.id = id;
        this.versions = versions;
    }

}
