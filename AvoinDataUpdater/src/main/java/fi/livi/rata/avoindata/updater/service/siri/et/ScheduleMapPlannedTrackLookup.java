package fi.livi.rata.avoindata.updater.service.siri.et;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * {@link PlannedTrackLookup} backed by the winning {@code Schedule} per train (the same map the generation
 * cycle already resolves). Indexes each schedule's rows into {@code trainNumber -> stationShortCode ->
 * commercialTrack}. Generation is single operating day, so {@code departureDate} is not part of the key; a
 * station revisited within a journey keeps its last planned track (rare — documented caveat).
 */
public class ScheduleMapPlannedTrackLookup implements PlannedTrackLookup {

    private final Map<Long, Map<String, String>> trackByTrainAndStation;

    public ScheduleMapPlannedTrackLookup(final Map<Long, Schedule> scheduleByTrainNumber) {
        this.trackByTrainAndStation = new HashMap<>();
        scheduleByTrainNumber.forEach((trainNumber, schedule) -> {
            final Map<String, String> byStation = new HashMap<>();
            if (schedule.scheduleRows != null) {
                for (final ScheduleRow row : schedule.scheduleRows) {
                    if (row.commercialTrack != null && row.station != null && row.station.stationShortCode != null) {
                        byStation.put(row.station.stationShortCode, row.commercialTrack);
                    }
                }
            }
            trackByTrainAndStation.put(trainNumber, byStation);
        });
    }

    @Override
    public Optional<String> plannedTrack(final long trainNumber, final LocalDate departureDate,
                                         final String stationShortCode) {
        final Map<String, String> byStation = trackByTrainAndStation.get(trainNumber);
        if (byStation == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byStation.get(stationShortCode));
    }
}
