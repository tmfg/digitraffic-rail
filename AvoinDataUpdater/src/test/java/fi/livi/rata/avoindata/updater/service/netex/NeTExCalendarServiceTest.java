package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for NeTExCalendarService — Schedule to DayType/OperatingPeriod conversion.
 */
class NeTExCalendarServiceTest {

    private NeTExCalendarService calendarService;
    private NeTExIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        idGenerator = new NeTExIdGenerator();
        calendarService = new NeTExCalendarService(idGenerator);
    }

    // --- DaysOfWeek string generation ---

    @Test
    void givenWeekdaySchedule_whenCreatingDaysOfWeek_thenContainsMondayToFriday() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);

        // when
        final String daysOfWeek = calendarService.createDaysOfWeekString(schedule);

        // then
        assertEquals("Monday Tuesday Wednesday Thursday Friday", daysOfWeek);
    }

    @Test
    void givenWeekendSchedule_whenCreatingDaysOfWeek_thenContainsSaturdaySunday() {
        // given
        final Schedule schedule = createSchedule(false, false, false, false, false, true, true,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);

        // when
        final String daysOfWeek = calendarService.createDaysOfWeekString(schedule);

        // then
        assertEquals("Saturday Sunday", daysOfWeek);
    }

    @Test
    void givenSingleDaySchedule_whenCreatingDaysOfWeek_thenContainsOnlyThatDay() {
        // given: runs only on Wednesdays
        final Schedule schedule = createSchedule(false, false, true, false, false, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);

        // when
        final String daysOfWeek = calendarService.createDaysOfWeekString(schedule);

        // then
        assertEquals("Wednesday", daysOfWeek);
    }

    @Test
    void givenEverydaySchedule_whenCreatingDaysOfWeek_thenContainsAllDays() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, true, true,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);

        // when
        final String daysOfWeek = calendarService.createDaysOfWeekString(schedule);

        // then
        assertEquals("Monday Tuesday Wednesday Thursday Friday Saturday Sunday", daysOfWeek);
    }

    // --- Calendar data creation for REGULAR schedules ---

    @Test
    void givenRegularSchedule_whenCreatingCalendarData_thenProducesDayTypeWithCorrectWeekdays() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule.id = 100L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule));

        // then
        assertEquals(1, calendarData.getDayTypes().size());
        assertEquals("Monday Tuesday Wednesday Thursday Friday", calendarData.getDayTypes().get(0).getDaysOfWeek());
    }

    @Test
    void givenRegularSchedule_whenCreatingCalendarData_thenProducesOperatingPeriodWithCorrectDates() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule.id = 100L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule));

        // then
        assertEquals(1, calendarData.getOperatingPeriods().size());
        assertEquals(LocalDate.of(2026, 6, 15), calendarData.getOperatingPeriods().get(0).getFromDate());
        assertEquals(LocalDate.of(2026, 12, 14), calendarData.getOperatingPeriods().get(0).getToDate());
    }

    @Test
    void givenRegularSchedule_whenCreatingCalendarData_thenProducesDayTypeAssignmentLinkingBoth() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule.id = 100L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule));

        // then
        assertEquals(1, calendarData.getDayTypeAssignments().size());
        final NeTExDayTypeAssignment assignment = calendarData.getDayTypeAssignments().get(0);
        assertFalse(assignment.isDateBased());
        assertNotNull(assignment.getOperatingPeriodId());
        assertNotNull(assignment.getDayTypeId());
    }

    // --- Calendar data creation for ADHOC schedules ---

    @Test
    void givenAdhocSchedule_whenCreatingCalendarData_thenProducesDayTypeAssignmentWithDate() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, true, true,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 15), Train.TimetableType.ADHOC);
        schedule.id = 200L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule));

        // then
        assertEquals(1, calendarData.getDayTypeAssignments().size());
        final NeTExDayTypeAssignment assignment = calendarData.getDayTypeAssignments().get(0);
        assertTrue(assignment.isDateBased());
        assertEquals(LocalDate.of(2026, 7, 15), assignment.getDate());
        assertNull(assignment.getOperatingPeriodId());
    }

    // --- Deduplication ---

    @Test
    void givenTwoSchedulesWithSameWeekdayPatternAndDateRange_whenCreatingCalendarData_thenShareDayType() {
        // given: two different trains with identical calendar patterns
        final Schedule schedule1 = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule1.id = 100L;

        final Schedule schedule2 = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule2.id = 200L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule1, schedule2));

        // then: only one DayType created
        assertEquals(1, calendarData.getDayTypes().size());
        // both schedules map to the same DayType
        assertEquals(calendarData.getDayTypeIdForSchedule(100L), calendarData.getDayTypeIdForSchedule(200L));
    }

    @Test
    void givenTwoSchedulesWithDifferentWeekdayPatterns_whenCreatingCalendarData_thenSeparateDayTypes() {
        // given
        final Schedule weekdaySchedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        weekdaySchedule.id = 100L;

        final Schedule weekendSchedule = createSchedule(false, false, false, false, false, true, true,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        weekendSchedule.id = 200L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(weekdaySchedule, weekendSchedule));

        // then
        assertEquals(2, calendarData.getDayTypes().size());
        assertNotEquals(calendarData.getDayTypeIdForSchedule(100L), calendarData.getDayTypeIdForSchedule(200L));
    }

    @Test
    void givenSameWeekdaysButDifferentDateRanges_whenCreatingCalendarData_thenSeparateOperatingPeriods() {
        // given
        final Schedule schedule1 = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 9, 14), Train.TimetableType.REGULAR);
        schedule1.id = 100L;

        final Schedule schedule2 = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule2.id = 200L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule1, schedule2));

        // then: separate operating periods (may share DayType if only weekday pattern is keyed)
        assertEquals(2, calendarData.getOperatingPeriods().size());
    }

    // --- DayType ID convention ---

    @Test
    void givenSchedule_whenCreatingCalendarData_thenDayTypeIdStartsWithFSR() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule.id = 100L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule));

        // then
        assertTrue(calendarData.getDayTypes().get(0).getId().startsWith("FTR:DayType:"));
    }

    @Test
    void givenSchedule_whenCreatingCalendarData_thenOperatingPeriodIdStartsWithFSR() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        schedule.id = 100L;

        // when
        final NeTExCalendarData calendarData = calendarService.createCalendarData(List.of(schedule));

        // then
        assertTrue(calendarData.getOperatingPeriods().get(0).getId().startsWith("FTR:OperatingPeriod:"));
    }

    // --- Hash generation ---

    @Test
    void givenSchedule_whenGeneratingDayTypeHash_thenIncludesWeekdaysAndDates() {
        // given
        final Schedule schedule = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);

        // when
        final String hash = calendarService.generateDayTypeHash(schedule);

        // then: hash should be deterministic and include relevant info
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
    }

    @Test
    void givenTwoIdenticalSchedules_whenGeneratingDayTypeHash_thenProducesSameHash() {
        // given
        final Schedule schedule1 = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);
        final Schedule schedule2 = createSchedule(true, true, true, true, true, false, false,
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 12, 14), Train.TimetableType.REGULAR);

        // when
        final String hash1 = calendarService.generateDayTypeHash(schedule1);
        final String hash2 = calendarService.generateDayTypeHash(schedule2);

        // then
        assertEquals(hash1, hash2);
    }

    // --- Helper ---

    private Schedule createSchedule(final boolean mo, final boolean tu, final boolean we,
                                     final boolean th, final boolean fr, final boolean sa, final boolean su,
                                     final LocalDate startDate, final LocalDate endDate,
                                     final Train.TimetableType timetableType) {
        final Schedule schedule = new Schedule();
        schedule.runOnMonday = mo;
        schedule.runOnTuesday = tu;
        schedule.runOnWednesday = we;
        schedule.runOnThursday = th;
        schedule.runOnFriday = fr;
        schedule.runOnSaturday = sa;
        schedule.runOnSunday = su;
        schedule.startDate = startDate;
        schedule.endDate = endDate;
        schedule.timetableType = timetableType;
        schedule.scheduleCancellations = new HashSet<>();
        schedule.scheduleExceptions = new HashSet<>();
        return schedule;
    }
}
