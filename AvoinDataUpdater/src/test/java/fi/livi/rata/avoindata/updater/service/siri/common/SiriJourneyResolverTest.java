package fi.livi.rata.avoindata.updater.service.siri.common;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SiriJourneyResolverTest {

    private final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
    private final SiriJourneyResolver resolver = new SiriJourneyResolver(idGenerator);

    // --- JOURNEY-01: REGULAR schedule produces correct serviceJourneyId ---

    @Test
    void givenRegularSchedule_whenResolveFromSchedule_thenServiceJourneyIdContainsTrainNumberAndScheduleId() {
        // given
        final Schedule schedule = new Schedule();
        schedule.trainNumber = 59L;
        schedule.id = 12345;
        schedule.timetableType = Train.TimetableType.REGULAR;
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:ServiceJourney:59-12345", result.serviceJourneyId());
    }

    // --- JOURNEY-02: REGULAR schedule — DataFrameRef equals departureDate ISO ---

    @Test
    void givenRegularSchedule_whenResolveFromSchedule_thenDataFrameRefEqualsDepartureDate() {
        // given
        final Schedule schedule = new Schedule();
        schedule.trainNumber = 59L;
        schedule.id = 12345;
        schedule.timetableType = Train.TimetableType.REGULAR;
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("2026-07-10", result.dataFrameRef());
    }

    // --- JOURNEY-03: ADHOC schedule uses startDate in id ---

    @Test
    void givenAdhocSchedule_whenResolveFromSchedule_thenServiceJourneyIdUsesStartDate() {
        // given
        final Schedule schedule = new Schedule();
        schedule.trainNumber = 9999L;
        schedule.startDate = LocalDate.of(2026, 7, 10);
        schedule.timetableType = Train.TimetableType.ADHOC;
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:ServiceJourney:9999-2026-07-10", result.serviceJourneyId());
    }

    // --- JOURNEY-04: ADHOC DataFrameRef equals departureDate (not startDate) ---

    @Test
    void givenAdhocScheduleWithDifferentDepartureDate_whenResolve_thenDataFrameRefUsesDepartureDate() {
        // given
        final Schedule schedule = new Schedule();
        schedule.trainNumber = 9999L;
        schedule.startDate = LocalDate.of(2026, 7, 10);
        schedule.timetableType = Train.TimetableType.ADHOC;
        final LocalDate departureDate = LocalDate.of(2026, 7, 11);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("2026-07-11", result.dataFrameRef());
    }

    // --- JOURNEY-05: Null trainNumber throws ---

    @Test
    void givenScheduleWithNullTrainNumber_whenResolveFromSchedule_thenThrowsNullPointerException() {
        // given
        final Schedule schedule = new Schedule();
        schedule.trainNumber = null;
        schedule.id = 1;
        schedule.timetableType = Train.TimetableType.REGULAR;
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when / then
        assertThrows(NullPointerException.class,
                () -> resolver.resolveFromSchedule(schedule, departureDate));
    }

    // --- JOURNEY-06: Different departureDate does not affect serviceJourneyId ---

    @Test
    void givenRegularSchedule_whenResolveWithDifferentDepartureDate_thenServiceJourneyIdUnchanged() {
        // given
        final Schedule schedule = new Schedule();
        schedule.trainNumber = 59L;
        schedule.id = 12345;
        schedule.timetableType = Train.TimetableType.REGULAR;
        final LocalDate departureDate = LocalDate.of(2026, 12, 1);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:ServiceJourney:59-12345", result.serviceJourneyId());
        assertEquals("2026-12-01", result.dataFrameRef());
    }
}
