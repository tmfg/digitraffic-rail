package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class TrafficRestrictionNotificationWithVersions {

    @ApiModelProperty("Traffic restriction notification id")
    public final String id;

    @ApiModelProperty("Traffic resctriction notification versions")
    public final List<TrafficRestrictionNotification> versions;

    public TrafficRestrictionNotificationWithVersions(String id, final List<TrafficRestrictionNotification> versions) {
        this.id = id;
        this.versions = versions;
    }

}
