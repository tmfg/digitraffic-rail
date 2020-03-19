package fi.livi.rata.avoindata.server.controller.api.ruma;

import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class TrafficRestrictionNotificationWithVersions {

    @ApiModelProperty("Traffic restriction notification id")
    public final String id;

    @ApiModelProperty("Traffic resctriction notification versions")
    public final List<SpatialTrafficRestrictionNotificationDto> versions;

    public TrafficRestrictionNotificationWithVersions(String id, final List<SpatialTrafficRestrictionNotificationDto> versions) {
        this.id = id;
        this.versions = versions;
    }

}
