package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * Tests for NeTExCalendarService — turning resolved operating dates into
 * DayTypes.
 */
class NeTExCalendarServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 3).with(DayOfWeek.MONDAY);

    private NeTExCalendarService calendarService;

    @BeforeEach
    void setUp() {
        calendarService = new NeTExCalendarService(new NeTExIdGenerator());
    }

    @Test
    void givenTwoJourneysOnTheSameDates_whenBuildingCalendar_thenTheyShareOneDayType() {
        // given
        final Map<TrainId, String> refs = new LinkedHashMap<>();
        refs.put(new TrainId(1L, MONDAY), "FTR:ServiceJourney:1");
        refs.put(new TrainId(2L, MONDAY), "FTR:ServiceJourney:2");

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then
        assertEquals(1, calendar.dayTypes().size());
        assertEquals(calendar.dayTypeRefByServiceJourney().get("FTR:ServiceJourney:1"),
                calendar.dayTypeRefByServiceJourney().get("FTR:ServiceJourney:2"));
    }

    @Test
    void givenJourneysOnDifferentDates_whenBuildingCalendar_thenDayTypesDiffer() {
        // given
        final Map<TrainId, String> refs = new LinkedHashMap<>();
        refs.put(new TrainId(1L, MONDAY), "FTR:ServiceJourney:1");
        refs.put(new TrainId(2L, MONDAY.plusDays(1)), "FTR:ServiceJourney:2");

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then
        assertEquals(2, calendar.dayTypes().size());
    }

    @Test
    void givenWeeklyPattern_whenBuildingCalendar_thenOnePeriodInsteadOfOneAssignmentPerDate() {
        // given: every Monday for six weeks
        final var refs = journeyOn(MONDAY, MONDAY.plusWeeks(1), MONDAY.plusWeeks(2),
                MONDAY.plusWeeks(3), MONDAY.plusWeeks(4), MONDAY.plusWeeks(5));

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then
        assertEquals(Set.of(DayOfWeek.MONDAY), calendar.dayTypes().get(0).daysOfWeek());
        assertEquals(1, calendar.operatingPeriods().size());
        assertEquals(MONDAY, calendar.operatingPeriods().get(0).from());
        assertEquals(MONDAY.plusWeeks(5), calendar.operatingPeriods().get(0).to());
        assertEquals(1, calendar.assignments().size());
        assertEquals(calendar.operatingPeriods().get(0).id(),
                calendar.assignments().get(0).operatingPeriodRef());
    }

    @Test
    void givenWeeklyPatternWithACancellation_whenBuildingCalendar_thenTheGapIsMarkedUnavailable() {
        // given: every Monday for six weeks, except the third
        final var refs = journeyOn(MONDAY, MONDAY.plusWeeks(1), MONDAY.plusWeeks(3),
                MONDAY.plusWeeks(4), MONDAY.plusWeeks(5));

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then: the pattern still collapses, with the missing day subtracted
        assertEquals(1, calendar.operatingPeriods().size());
        final var unavailable = calendar.assignments().stream().filter(a -> !a.available()).toList();
        assertEquals(1, unavailable.size());
        assertEquals(MONDAY.plusWeeks(2), unavailable.get(0).date());
    }

    @Test
    void givenScatteredDates_whenBuildingCalendar_thenDatesAreEnumeratedWithoutAPeriod() {
        // given: one Monday and one Wednesday a fortnight later, so a Monday+Wednesday
        // mask over the span would pull in four days the journey does not run
        final var refs = journeyOn(MONDAY, MONDAY.plusWeeks(2).plusDays(2));

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then
        assertTrue(calendar.operatingPeriods().isEmpty());
        assertTrue(calendar.dayTypes().get(0).daysOfWeek().isEmpty());
        assertEquals(2, calendar.assignments().size());
        assertTrue(calendar.assignments().stream().allMatch(a -> a.date() != null));
    }

    @Test
    void givenAnyCalendar_whenBuildingAssignments_thenEachDayTypeHasDistinctOrders() {
        // given: order is part of the DayTypeAssignment schema key, and a collapsed
        // pattern mixes a period assignment with per-date exceptions
        final var refs = journeyOn(MONDAY, MONDAY.plusWeeks(1), MONDAY.plusWeeks(3),
                MONDAY.plusWeeks(4), MONDAY.plusWeeks(5));

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then
        final List<Integer> orders = calendar.assignments().stream()
                .map(NeTExCalendarService.NeTExDayTypeAssignment::order).toList();
        assertEquals(orders.size(), Set.copyOf(orders).size());
        assertFalse(orders.contains(0), "order is 1-based");
    }

    @Test
    void givenTheSameDates_whenBuildingCalendarTwice_thenDayTypeIdsAreStable() {
        // given
        final var refs = journeyOn(MONDAY, MONDAY.plusWeeks(1));

        // when
        final var first = calendarService.createCalendarData(refs);
        final var second = new NeTExCalendarService(new NeTExIdGenerator()).createCalendarData(refs);

        // then
        assertEquals(first.dayTypes().get(0).id(), second.dayTypes().get(0).id());
    }

    @Test
    void givenNoJourneys_whenBuildingCalendar_thenCalendarIsEmpty() {
        final var calendar = calendarService.createCalendarData(Map.of());

        assertTrue(calendar.dayTypes().isEmpty());
        assertTrue(calendar.assignments().isEmpty());
        assertTrue(calendar.operatingPeriods().isEmpty());
    }

    @Test
    void givenJourneyDates_whenAskingForDates_thenTheyComeBackSortedAndDeduplicated() {
        // given: two journeys sharing a date
        final Map<TrainId, String> refs = new LinkedHashMap<>();
        refs.put(new TrainId(1L, MONDAY.plusDays(1)), "FTR:ServiceJourney:1");
        refs.put(new TrainId(1L, MONDAY), "FTR:ServiceJourney:1");
        refs.put(new TrainId(2L, MONDAY), "FTR:ServiceJourney:2");

        // when
        final var calendar = calendarService.createCalendarData(refs);

        // then
        assertEquals(List.of(MONDAY, MONDAY.plusDays(1)),
                calendar.datesOf(List.of("FTR:ServiceJourney:1", "FTR:ServiceJourney:2")));
    }

    private static Map<TrainId, String> journeyOn(final LocalDate... dates) {
        final Map<TrainId, String> refs = new LinkedHashMap<>();
        for (final LocalDate date : dates) {
            refs.put(new TrainId(1L, date), "FTR:ServiceJourney:1");
        }
        return refs;
    }
}
