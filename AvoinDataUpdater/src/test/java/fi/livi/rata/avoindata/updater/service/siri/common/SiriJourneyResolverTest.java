package fi.livi.rata.avoindata.updater.service.siri.common;

import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class SiriJourneyResolverTest {

    private final NeTExIdGenerator idGenerator = new NeTExIdGenerator();
    private final SiriJourneyResolver resolver = new SiriJourneyResolver(idGenerator);

    private Schedule regularSchedule(final long trainNumber, final long id) {
        final Schedule schedule = new Schedule();
        schedule.trainNumber = trainNumber;
        schedule.id = id;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.trainType = new TrainType("IC");
        return schedule;
    }

    // --- JOURNEY-01: REGULAR schedule produces correct serviceJourneyId ---

    @Test
    void givenRegularSchedule_whenResolveFromSchedule_thenServiceJourneyIdContainsTrainNumberAndScheduleId() {
        // given
        final Schedule schedule = regularSchedule(59L, 12345);
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
        final Schedule schedule = regularSchedule(59L, 12345);
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
        schedule.trainType = new TrainType("IC");
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
        schedule.trainType = new TrainType("IC");
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
        schedule.trainType = new TrainType("IC");
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when / then
        assertThrows(NullPointerException.class,
                () -> resolver.resolveFromSchedule(schedule, departureDate));
    }

    // --- JOURNEY-06: Different departureDate does not affect serviceJourneyId ---

    @Test
    void givenRegularSchedule_whenResolveWithDifferentDepartureDate_thenServiceJourneyIdUnchanged() {
        // given
        final Schedule schedule = regularSchedule(59L, 12345);
        final LocalDate departureDate = LocalDate.of(2026, 12, 1);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:ServiceJourney:59-12345", result.serviceJourneyId());
        assertEquals("2026-12-01", result.dataFrameRef());
    }

    // --- JOURNEY-07: lineId from trainType.name when commuterLineId is null ---

    @Test
    void givenScheduleWithNoCommuterLineId_whenResolve_thenLineIdDerivedFromTrainTypeName() {
        // given
        final Schedule schedule = regularSchedule(59L, 12345);
        schedule.commuterLineId = null;
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:Line:IC", result.lineId());
    }

    // --- JOURNEY-08: lineId from commuterLineId when present ---

    @Test
    void givenScheduleWithCommuterLineId_whenResolve_thenLineIdUsesCommuterLineId() {
        // given
        final Schedule schedule = regularSchedule(59L, 12345);
        schedule.commuterLineId = "A";
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:Line:A", result.lineId());
    }

    // --- JOURNEY-09: blank commuterLineId falls back to trainType.name ---

    @Test
    void givenScheduleWithBlankCommuterLineId_whenResolve_thenLineIdFallsBackToTrainType() {
        // given
        final Schedule schedule = regularSchedule(59L, 12345);
        schedule.commuterLineId = "   ";
        final LocalDate departureDate = LocalDate.of(2026, 7, 10);

        // when
        final ResolvedJourney result = resolver.resolveFromSchedule(schedule, departureDate);

        // then
        assertEquals("DT:Line:IC", result.lineId());
    }
}
