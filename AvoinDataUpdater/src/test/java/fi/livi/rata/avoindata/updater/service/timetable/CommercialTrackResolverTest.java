package fi.livi.rata.avoindata.updater.service.timetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

class CommercialTrackResolverTest {

    private static final long TRAIN = 59L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 3);

    private final CommercialTrackResolver resolver = new CommercialTrackResolver();

    @Test
    void givenTimetableRowForSameStopPart_whenResolving_thenTrackIsTaken() {
        final ScheduleRow row = scheduleRow(100L, 101L);

        final var rows = List.of(timeTableRow(100L, TimeTableRow.TimeTableRowType.ARRIVAL, "4"));

        assertEquals("4", resolver.resolveTrack(row, rows).orElse(null));
    }

    @Test
    void givenOriginWithoutArrival_whenResolving_thenDepartureRowIsUsed() {
        final ScheduleRow origin = scheduleRow(null, 101L);

        final var rows = List.of(timeTableRow(101L, TimeTableRow.TimeTableRowType.DEPARTURE, "6"));

        assertEquals("6", resolver.resolveTrack(origin, rows).orElse(null));
    }

    @Test
    void givenTimetableRowForAnotherStop_whenResolving_thenNothingIsTaken() {
        final ScheduleRow row = scheduleRow(100L, 101L);

        final var rows = List.of(timeTableRow(999L, TimeTableRow.TimeTableRowType.ARRIVAL, "4"));

        assertTrue(resolver.resolveTrack(row, rows).isEmpty());
    }

    /** An arrival row must not answer for a departure part that shares its id. */
    @Test
    void givenMatchingIdButWrongType_whenResolving_thenNothingIsTaken() {
        final ScheduleRow origin = scheduleRow(null, 101L);

        final var rows = List.of(timeTableRow(101L, TimeTableRow.TimeTableRowType.ARRIVAL, "6"));

        assertTrue(resolver.resolveTrack(origin, rows).isEmpty());
    }

    @Test
    void givenBlankTrack_whenSelectingRowsForSchedule_thenRowIsDropped() {
        final Schedule schedule = schedule();

        final var rows = resolver.rowsForSchedule(schedule, resolver.byTrainNumber(List.of(
                timeTableRow(100L, TimeTableRow.TimeTableRowType.ARRIVAL, ""),
                timeTableRow(101L, TimeTableRow.TimeTableRowType.DEPARTURE, "2"))));

        assertEquals(1, rows.size());
        assertEquals("2", rows.get(0).commercialTrack);
    }

    @Test
    void givenDayTheScheduleDoesNotRun_whenSelectingRowsForSchedule_thenRowIsDropped() {
        final Schedule schedule = schedule();
        schedule.startDate = TODAY.plusDays(5);
        schedule.endDate = TODAY.plusDays(5);

        final var rows = resolver.rowsForSchedule(schedule, resolver.byTrainNumber(List.of(
                timeTableRow(100L, TimeTableRow.TimeTableRowType.ARRIVAL, "4"))));

        assertTrue(rows.isEmpty());
    }

    @Test
    void givenDaysDisagreeOnTrack_whenResolving_thenNearestDayWins() {
        final ScheduleRow row = scheduleRow(100L, 101L);
        final ZonedDateTime now = DateProvider.nowInHelsinki();

        // deliberately out of chronological order: the answer must not depend on it
        final var rows = List.of(
                timeTableRowAt(100L, "9", now.plusDays(7)),
                timeTableRowAt(100L, "4", now.plusDays(1)),
                timeTableRowAt(100L, "5", now.plusDays(3)));

        assertEquals("4", resolver.resolveTrack(row, rows).orElse(null));
    }

    @Test
    void givenYesterdayIsNearerThanTheNextRun_whenResolving_thenYesterdayWins() {
        final ScheduleRow row = scheduleRow(100L, 101L);
        final ZonedDateTime now = DateProvider.nowInHelsinki();

        final var rows = List.of(
                timeTableRowAt(100L, "9", now.plusDays(6)),
                timeTableRowAt(100L, "4", now.minusHours(20)));

        assertEquals("4", resolver.resolveTrack(row, rows).orElse(null));
    }

    private static ScheduleRow scheduleRow(final Long arrivalId, final Long departureId) {
        final ScheduleRow row = new ScheduleRow();
        final StationEmbeddable station = new StationEmbeddable();
        station.stationShortCode = "HKI";
        row.station = station;
        if (arrivalId != null) {
            row.arrival = new ScheduleRowPart();
            row.arrival.id = arrivalId;
            row.arrival.timestamp = Duration.ofHours(8);
        }
        if (departureId != null) {
            row.departure = new ScheduleRowPart();
            row.departure.id = departureId;
            row.departure.timestamp = Duration.ofHours(8).plusMinutes(2);
        }
        return row;
    }

    private static Schedule schedule() {
        final Schedule schedule = new Schedule();
        schedule.trainNumber = TRAIN;
        schedule.typeCode = "R";
        schedule.startDate = TODAY.minusDays(30);
        schedule.endDate = TODAY.plusDays(30);
        schedule.runOnMonday = true;
        schedule.runOnTuesday = true;
        schedule.runOnWednesday = true;
        schedule.runOnThursday = true;
        schedule.runOnFriday = true;
        schedule.runOnSaturday = true;
        schedule.runOnSunday = true;
        schedule.scheduleRows = new ArrayList<>();
        schedule.scheduleExceptions = new HashSet<>();
        schedule.scheduleCancellations = new HashSet<>();
        return schedule;
    }

    private static SimpleTimeTableRow timeTableRow(final long attapId,
            final TimeTableRow.TimeTableRowType type, final String track) {
        return new SimpleTimeTableRow(attapId, TODAY, TRAIN, track,
                ZonedDateTime.of(TODAY.atTime(8, 0), java.time.ZoneId.of("Europe/Helsinki")),
                "HKI", type);
    }

    private static SimpleTimeTableRow timeTableRowAt(final long attapId, final String track,
            final ZonedDateTime scheduledTime) {
        return new SimpleTimeTableRow(attapId, scheduledTime.toLocalDate(), TRAIN, track, scheduledTime,
                "HKI", TimeTableRow.TimeTableRowType.ARRIVAL);
    }
}
