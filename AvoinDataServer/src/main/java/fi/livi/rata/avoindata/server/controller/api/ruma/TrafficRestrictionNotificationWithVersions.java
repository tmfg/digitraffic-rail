package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TrafficRestrictionNotificationWithVersions", title = "TrafficRestrictionNotificationWithVersions")
public class TrafficRestrictionNotificationWithVersions {

    @Schema(description = "Traffic restriction notification id")
    public final String id;

    @Schema(description = "Traffic resctriction notification versions")
    public final List<SpatialTrafficRestrictionNotificationDto> versions;

    public TrafficRestrictionNotificationWithVersions(String id, final List<SpatialTrafficRestrictionNotificationDto> versions) {
        this.id = id;
        this.versions = versions;
    }

}
