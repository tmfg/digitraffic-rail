package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TrackWorkNotificationWithVersions", title = "TrackWorkNotificationWithVersions")
public class TrackWorkNotificationWithVersions {

    @Schema(description = "Track work notification id")
    public final String id;

    @Schema(description = "Track work notification versions")
    public final List<SpatialTrackWorkNotificationDto> versions;

    public TrackWorkNotificationWithVersions(String id, final List<SpatialTrackWorkNotificationDto> versions) {
        this.id = id;
        this.versions = versions;
    }

}
