package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.ZonedDateTime;
import java.util.Set;

import fi.livi.rata.avoindata.common.domain.spatial.GeometryDto;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SpatialTrafficRestrictionNotification", title = "SpatialTrafficRestrictionNotification", description = "Describes a restriction affecting the use of a railway infrastructure part")
public class SpatialTrafficRestrictionNotificationDto extends TrafficRestrictionNotificationDto {

    @Schema(description = "Approximate location", required = true)
    public final GeometryDto<?> location;

    @Schema(description = "Locations", required = true)
    public final Set<SpatialRumaLocationDto> locations;

    public SpatialTrafficRestrictionNotificationDto(
            final TrafficRestrictionNotification.TrafficRestrictionNotificationId id,
            final TrafficRestrictionNotificationState state,
            final String organization,
            final ZonedDateTime created,
            final ZonedDateTime modified,
            final TrafficRestrictionType limitation,
            final String trackWorkNotificationId,
            final Double axleWeightMax,
            final ZonedDateTime startDate,
            final ZonedDateTime endDate,
            final ZonedDateTime finished,
            final GeometryDto<?> location,
            final Set<SpatialRumaLocationDto> locations)
    {
        super(id, state, organization, created, modified, limitation, trackWorkNotificationId, axleWeightMax, startDate, endDate, finished);
        this.location = location;
        this.locations = locations;
    }

}
