package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "A logical part of a track work")
public class TrackWorkPartDto {

    @ApiModelProperty(value = "Index number", required = true)
    public final Long partIndex;

    @ApiModelProperty(value = "Planned (not necessarily actual) start day", required = true)
    public final LocalDate startDay;

    @ApiModelProperty(
            value = "Requested minimum duration for work permission in ISO 8601 format, e.g. PT30M",
            dataType = "java.lang.String",
            required = true)
    public final Duration permissionMinimumDuration;

    @ApiModelProperty(value = "Contains fire work", required = true)
    public final Boolean containsFireWork;

    @ApiModelProperty(value = "Planned working gap")
    public final LocalTime plannedWorkingGap;

    @ApiModelProperty(value = "Related advance notifications")
    public final List<String> advanceNotifications;

    @ApiModelProperty(value = "Locations", required = true)
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
