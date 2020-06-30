package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import io.swagger.annotations.ApiModelProperty;

public class TrackWorkPartDto {

    @ApiModelProperty("Index number")
    public final Long partIndex;

    @ApiModelProperty("Planned (not necessarily actual) start day")
    public final LocalDate startDay;

    @ApiModelProperty(value = "Requested minimum duration for work permission", dataType = "fi.livi.rata.avoindata.server.dto.SwaggerObject")
    public final Duration permissionMinimumDuration;

    @ApiModelProperty("Contains fire work")
    public final Boolean containsFireWork;

    @ApiModelProperty("Planned working gap")
    public final LocalTime plannedWorkingGap;

    @ApiModelProperty("Related advance notifications")
    public final List<String> advanceNotifications;

    @ApiModelProperty("Locations")
    public final Set<RumaLocationDto> locations;

    public TrackWorkPartDto(
            final Long partIndex,
            final LocalDate startDay,
            final Duration permissionMinimumDuration,
            final Boolean containsFireWork,
            final LocalTime plannedWorkingGap,
            final List<String> advanceNotifications,
            final Set<RumaLocationDto> locations)
    {
        this.partIndex = partIndex;
        this.startDay = startDay;
        this.permissionMinimumDuration = permissionMinimumDuration;
        this.containsFireWork = containsFireWork;
        this.plannedWorkingGap = plannedWorkingGap;
        this.advanceNotifications = advanceNotifications;
        this.locations = locations;
    }
}
