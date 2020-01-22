package fi.livi.rata.avoindata.server.controller.api.ruma;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public class TrackWorkPartDto {

    public final Long partIndex;
    public final LocalDate startDay;
    public final Duration permissionMinimumDuration;
    public final Boolean containsFireWork;
    public final LocalTime plannedWorkingGap;
    public final List<String> advanceNotifications;
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
