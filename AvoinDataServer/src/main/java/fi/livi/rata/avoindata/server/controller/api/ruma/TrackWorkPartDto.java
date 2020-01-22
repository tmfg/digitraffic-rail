package fi.livi.rata.avoindata.server.controller.api.ruma;

import io.swagger.annotations.ApiModelProperty;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public class TrackWorkPartDto {

    @ApiModelProperty("Index number")
    public final Long partIndex;

    @ApiModelProperty("Planned (not necessarily actual) start day")
    public final LocalDate startDay;

    @ApiModelProperty("Requested minimum duration for work permission")
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
