package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * RIPA's schedules endpoint rarely fills in liikennepaikanRaide, but its trains
 * endpoint carries kaupallinenNro on the timetable rows and those reach
 * time_table_row a few days ahead of departure. This joins the two on attapId,
 * the schedule row part a timetable row came from.
 */
@Component
public class CommercialTrackResolver {

    public Map<Long, List<SimpleTimeTableRow>> byTrainNumber(final List<SimpleTimeTableRow> timeTableRows) {
        return timeTableRows.stream().collect(Collectors.groupingBy(SimpleTimeTableRow::getTrainNumber));
    }

    /**
     * Restricts the rows to days the schedule actually runs, so a track cannot be
     * taken from a day another version of the schedule was in effect.
     */
    public List<SimpleTimeTableRow> rowsForSchedule(final Schedule schedule,
            final Map<Long, List<SimpleTimeTableRow>> byTrainNumber) {
        return byTrainNumber.getOrDefault(schedule.trainNumber, Collections.emptyList())
                .stream()
                .filter(r -> StringUtils.isNotBlank(r.commercialTrack))
                .filter(r -> schedule.isRunOnDay(r.scheduledTime.toLocalDate()))
                .toList();
    }

    /**
     * Every day in the window carries the same stop and they can name different
     * tracks, so the run nearest to now wins: it is both the likeliest to still hold
     * and the only choice that does not vary between generations.
     */
    public Optional<String> resolveTrack(final ScheduleRow scheduleRow,
            final List<SimpleTimeTableRow> rowsForSchedule) {
        final ZonedDateTime now = DateProvider.nowInHelsinki();
        return rowsForSchedule.stream()
                .filter(row -> matches(row, scheduleRow))
                .min(Comparator
                        .comparing((SimpleTimeTableRow row) -> Duration.between(now, row.scheduledTime).abs())
                        .thenComparing(row -> row.scheduledTime))
                .map(row -> row.commercialTrack);
    }

    private boolean matches(final SimpleTimeTableRow timeTableRow, final ScheduleRow scheduleRow) {
        if (scheduleRow.arrival != null) {
            return timeTableRow.type.equals(TimeTableRow.TimeTableRowType.ARRIVAL)
                    && timeTableRow.id.attapId.equals(scheduleRow.arrival.id);
        } else if (scheduleRow.departure != null) {
            return timeTableRow.type.equals(TimeTableRow.TimeTableRowType.DEPARTURE)
                    && timeTableRow.id.attapId.equals(scheduleRow.departure.id);
        }
        return false;
    }
}
