package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.netex.NeTExIdGenerator;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriJourneyResolver;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

class SiriScheduleMapJourneyRefResolverTest {

    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);

    private ScheduleMapJourneyRefResolver resolver;

    @BeforeEach
    void setUp() {
        // given — two schedules in the map: train 59 (REGULAR) and train 100 (ADHOC)
        final Schedule regularSchedule = createSchedule(59L, 12345L, Train.TimetableType.REGULAR, "IC");
        final Schedule adhocSchedule = createSchedule(100L, 9999L, Train.TimetableType.ADHOC, "S");
        adhocSchedule.startDate = DEPARTURE_DATE;

        final Map<Long, Schedule> scheduleMap = Map.of(
                59L, regularSchedule,
                100L, adhocSchedule
        );

        final SiriJourneyResolver journeyResolver = new SiriJourneyResolver(new NeTExIdGenerator());
        resolver = new ScheduleMapJourneyRefResolver(scheduleMap, journeyResolver);
    }

    // --- SMJR-01: REGULAR train resolves to ServiceJourney with schedule id ---

    @Test
    void givenRegularTrain_whenResolve_thenServiceJourneyIdContainsScheduleId() {
        // when
        final Optional<ResolvedJourney> result = resolver.resolve(59L, DEPARTURE_DATE);

        // then
        assertTrue(result.isPresent());
        assertEquals("DT:ServiceJourney:59-12345", result.get().serviceJourneyId());
    }

    // --- SMJR-02: ADHOC train resolves to ServiceJourney with adhoc id format ---

    @Test
    void givenAdhocTrain_whenResolve_thenServiceJourneyIdHasAdhocFormat() {
        // when
        final Optional<ResolvedJourney> result = resolver.resolve(100L, DEPARTURE_DATE);

        // then
        assertTrue(result.isPresent());
        assertEquals("DT:ServiceJourney:100-2026-07-15", result.get().serviceJourneyId());
    }

    // --- SMJR-03: Unknown train number → empty ---

    @Test
    void givenUnknownTrainNumber_whenResolve_thenEmpty() {
        // when
        final Optional<ResolvedJourney> result = resolver.resolve(999L, DEPARTURE_DATE);

        // then
        assertEquals(Optional.empty(), result);
    }

    // --- SMJR-04: DataFrameRef matches departure date ---

    @Test
    void givenAnySchedule_whenResolveWithDate_thenDataFrameRefMatchesDate() {
        // given
        final LocalDate differentDate = LocalDate.of(2026, 8, 1);

        // when
        final Optional<ResolvedJourney> result = resolver.resolve(59L, differentDate);

        // then
        assertTrue(result.isPresent());
        assertEquals("2026-08-01", result.get().dataFrameRef());
    }

    // ===== HELPERS =====

    private static Schedule createSchedule(final long trainNumber, final long id,
                                           final Train.TimetableType timetableType,
                                           final String typeName) {
        final Schedule schedule = new Schedule();
        schedule.trainNumber = trainNumber;
        schedule.id = id;
        schedule.timetableType = timetableType;
        schedule.startDate = LocalDate.of(2026, 1, 1);
        schedule.trainType = new fi.livi.rata.avoindata.common.domain.localization.TrainType(typeName);
        schedule.commuterLineId = null;
        return schedule;
    }
}
