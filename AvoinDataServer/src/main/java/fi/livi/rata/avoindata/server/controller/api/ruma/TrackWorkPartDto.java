package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TrackWorkPart", title = "TrackWorkPart", description = "A logical part of a track work")
public class TrackWorkPartDto {

    @Schema(description = "Index number", requiredMode = Schema.RequiredMode.REQUIRED)
    public final Long partIndex;

    @Schema(description = "Planned (not necessarily actual) start day", requiredMode = Schema.RequiredMode.REQUIRED)
    public final LocalDate startDay;

    @Schema(description = "Requested minimum duration for work permission in ISO 8601 format, e.g. PT30M",
            type = "java.lang.String",
            implementation = java.lang.String.class,
            requiredMode = Schema.RequiredMode.REQUIRED)
    public final Duration permissionMinimumDuration;

    @Schema(description = "Contains fire work", requiredMode = Schema.RequiredMode.REQUIRED)
    public final Boolean containsFireWork;

    @Schema(description = "Planned working gap in local time with no time zone, e.g. 11:43:00",
            type = "java.lang.String", implementation = java.lang.String.class)
    public final LocalTime plannedWorkingGap;

    @Schema(description = "Related advance notifications")
    public final List<String> advanceNotifications;

    @Schema(description = "Locations", requiredMode = Schema.RequiredMode.REQUIRED)
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
