package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation.ScheduleCancellationType;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow.ScheduleRowStopType;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

class NeTExShortenedSchedulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    private final NeTExShortenedSchedules partialCancellations = new NeTExShortenedSchedules();

    @Test
    void givenNoCancellations_whenEffectiveOn_thenReturnsSameSchedule() {
        final Schedule schedule = schedule(4);

        assertSame(schedule, partialCancellations.effectiveOn(schedule, DAY).orElseThrow());
    }

    @Test
    void givenWholeDayCancellation_whenEffectiveOn_thenIgnoresIt() {
        final Schedule schedule = schedule(4);
        // A whole-day cancellation cancels the whole train, not stops. Such a train is dropped before
        // effectiveOn is called, so effectiveOn must not make a copy of it. Stop 1 is listed as cancelled
        // only so that the test fails if the WHOLE_DAY type is not skipped.
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.WHOLE_DAY, DAY, DAY, schedule, 1));

        assertSame(schedule, partialCancellations.effectiveOn(schedule, DAY).orElseThrow());
    }

    @Test
    void givenCancellationOnOtherDay_whenEffectiveOn_thenReturnsSameSchedule() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY,
                DAY.plusDays(1), DAY.plusDays(1), schedule, 1));

        assertSame(schedule, partialCancellations.effectiveOn(schedule, DAY).orElseThrow());
    }

    @Test
    void givenPartialCancellation_whenEffectiveOn_thenStopIsLeftOut() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, schedule, 1));

        final Schedule effective = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();

        assertEquals(3, effective.scheduleRows.size());
        assertEquals(List.of(0L, 2L, 3L), effective.scheduleRows.stream().map(row -> row.id).toList());
        assertEquals(4, schedule.scheduleRows.size());
        assertNotEquals(String.valueOf(schedule.id), effective.journeyKey());
    }

    @Test
    void givenDifferentRouteCancellation_whenEffectiveOn_thenStopIsLeftOut() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(
                cancellation(1, ScheduleCancellationType.DIFFERENT_ROUTE, DAY, DAY, schedule, 2));

        final Schedule effective = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();

        assertEquals(List.of(0L, 1L, 3L), effective.scheduleRows.stream().map(row -> row.id).toList());
    }

    @Test
    void givenOverlappingCancellations_whenEffectiveOn_thenAllTheirStopsAreLeftOut() {
        final Schedule schedule = schedule(5);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, schedule, 1));
        schedule.scheduleCancellations.add(
                cancellation(2, ScheduleCancellationType.DIFFERENT_ROUTE, DAY, DAY, schedule, 1, 3));

        final Schedule effective = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();

        assertEquals(List.of(0L, 2L, 4L), effective.scheduleRows.stream().map(row -> row.id).toList());
    }

    @Test
    void givenOnlyArrivalCancelledOnStopWithBothSides_whenEffectiveOn_thenStopStays() {
        final Schedule schedule = schedule(4);
        final ScheduleCancellation cancellation = cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY,
                schedule);
        cancellation.cancelledRows.add(schedule.scheduleRows.get(1).arrival);
        schedule.scheduleCancellations.add(cancellation);

        assertSame(schedule, partialCancellations.effectiveOn(schedule, DAY).orElseThrow());
    }

    @Test
    void givenSameStopsCancelledOnTwoDays_whenEffectiveOn_thenSameCopyAndKey() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(
                cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY.plusDays(1), schedule, 1));

        final Schedule first = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();
        final Schedule second = partialCancellations.effectiveOn(schedule, DAY.plusDays(1)).orElseThrow();

        assertSame(first, second);
    }

    @Test
    void givenSameStopsCancelledInSeparateRuns_whenEffectiveOn_thenKeyIsTheSame() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, schedule, 1));

        final String first = partialCancellations.effectiveOn(schedule, DAY).orElseThrow().journeyKey();
        final String second = new NeTExShortenedSchedules().effectiveOn(schedule, DAY).orElseThrow().journeyKey();

        assertEquals(first, second);
    }

    @Test
    void givenDifferentStopsCancelledOnDifferentDays_whenEffectiveOn_thenDifferentKeys() {
        final Schedule schedule = schedule(5);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, schedule, 1));
        schedule.scheduleCancellations.add(
                cancellation(2, ScheduleCancellationType.PARTIALLY, DAY.plusDays(1), DAY.plusDays(1), schedule, 2));

        final Schedule first = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();
        final Schedule second = partialCancellations.effectiveOn(schedule, DAY.plusDays(1)).orElseThrow();

        assertNotEquals(first.journeyKey(), second.journeyKey());
    }

    @Test
    void givenPartialCancellation_whenEffectiveOn_thenRealtimeRowsKeepTheCancelledStop() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, schedule, 1));

        final Schedule effective = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();

        assertEquals(List.of(0L, 1L, 2L, 3L), effective.realtimeRows.stream().map(row -> row.id).toList());
    }

    @Test
    void givenDifferentRouteCancellation_whenEffectiveOn_thenRealtimeRowsLeaveTheStopOut() {
        final Schedule schedule = schedule(4);
        schedule.scheduleCancellations.add(
                cancellation(1, ScheduleCancellationType.DIFFERENT_ROUTE, DAY, DAY, schedule, 2));

        final Schedule effective = partialCancellations.effectiveOn(schedule, DAY).orElseThrow();

        assertEquals(List.of(0L, 1L, 3L), effective.realtimeRows.stream().map(row -> row.id).toList());
    }

    @Test
    void givenSameStopRemovedForDifferentReasons_whenEffectiveOn_thenCopiesHaveDifferentKeys() {
        final Schedule partial = schedule(4);
        partial.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, partial, 1));
        final Schedule differentRoute = schedule(4);
        differentRoute.scheduleCancellations.add(
                cancellation(1, ScheduleCancellationType.DIFFERENT_ROUTE, DAY, DAY, differentRoute, 1));

        final Schedule partialCopy = partialCancellations.effectiveOn(partial, DAY).orElseThrow();
        final Schedule differentRouteCopy = partialCancellations.effectiveOn(differentRoute, DAY).orElseThrow();

        assertNotEquals(partialCopy.journeyKey(), differentRouteCopy.journeyKey());
    }

    @Test
    void givenFewerThanTwoCommercialStopsLeft_whenEffectiveOn_thenEmpty() {
        final Schedule schedule = schedule(3);
        schedule.scheduleCancellations.add(cancellation(1, ScheduleCancellationType.PARTIALLY, DAY, DAY, schedule, 1, 2));

        final Optional<Schedule> effective = partialCancellations.effectiveOn(schedule, DAY);

        assertTrue(effective.isEmpty());
    }

    private static Schedule schedule(final int stops) {
        final Schedule schedule = new Schedule();
        schedule.id = 7L;
        schedule.scheduleRows = new ArrayList<>();
        schedule.scheduleCancellations = new HashSet<>();
        for (int i = 0; i < stops; i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = i;
            row.arrival = i == 0 ? null : part(100L + i);
            row.departure = i == stops - 1 ? null : part(200L + i);
            schedule.scheduleRows.add(row);
        }
        return schedule;
    }

    private static ScheduleRowPart part(final long id) {
        final ScheduleRowPart part = new ScheduleRowPart();
        part.id = id;
        part.stopType = ScheduleRowStopType.COMMERCIAL;
        return part;
    }

    private static ScheduleCancellation cancellation(final long id, final ScheduleCancellationType type,
            final LocalDate from, final LocalDate to, final Schedule schedule, final int... rowIndexes) {
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.id = id;
        cancellation.scheduleCancellationType = type;
        cancellation.startDate = from;
        cancellation.endDate = to;
        for (final int index : rowIndexes) {
            final ScheduleRow row = schedule.scheduleRows.get(index);
            if (row.arrival != null) {
                cancellation.cancelledRows.add(row.arrival);
            }
            if (row.departure != null) {
                cancellation.cancelledRows.add(row.departure);
            }
        }
        return cancellation;
    }
}
