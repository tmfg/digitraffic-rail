package fi.livi.rata.avoindata.updater.service.timetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.TrackObservation;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;

class HistoricalTrackSourceTest {

    private static final long TRAIN = 59L;
    private static final int HISTORY_DAYS = 60;
    private static final TimeTableRow.TimeTableRowType ARRIVAL = TimeTableRow.TimeTableRowType.ARRIVAL;

    private TimeTableRowRepository repository;
    private HistoricalTrackSource source;

    @BeforeEach
    void setUp() {
        repository = mock(TimeTableRowRepository.class);
        when(repository.findObservedTracks(any(), any(), anyCollection())).thenReturn(List.of());
        source = new HistoricalTrackSource(repository);
        ReflectionTestUtils.setField(source, "historyDays", HISTORY_DAYS);
    }

    @Test
    void givenNothingWanted_whenResolving_thenNothingIsQueried() {
        assertTrue(source.resolve(Set.of()).isEmpty());
        verify(repository, never()).findObservedTracks(any(), any(), anyCollection());
    }

    @Test
    void givenAStopWasSeen_whenResolving_thenItsTrackIsUsed() {
        observed(observation(100L, "HKI", "4", 1));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertEquals("4", source.resolve(Set.of(key)).get(key));
    }

    @Test
    void givenSeveralSightings_whenResolving_thenTheMostRecentWins() {
        observed(observation(100L, "HKI", "4", 1), observation(100L, "HKI", "9", 2));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertEquals("4", source.resolve(Set.of(key)).get(key));
    }

    @Test
    void givenOnlyAnotherSchedulePartAtTheStation_whenResolving_thenTheStationKeyAnswers() {
        observed(observation(777L, "HKI", "9", 1));

        final var stationKey = HistoricalTrackSource.forStation(TRAIN, "HKI", ARRIVAL);
        assertEquals("9", source.resolve(Set.of(stationKey)).get(stationKey));
    }

    @Test
    void givenAnotherTrain_whenResolving_thenNothingIsUsed() {
        observed(new TrackObservation(999L, 100L, "HKI", ARRIVAL, "4", daysAgo(1)));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertNull(source.resolve(Set.of(key)).get(key));
    }

    @Test
    void givenGapsOnTwoTrains_whenResolving_thenOnlyThoseTrainsAreAskedAbout() {
        source.resolve(Set.of(
                HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL),
                HistoricalTrackSource.forStation(TRAIN, "HKI", ARRIVAL),
                HistoricalTrackSource.forSchedulePart(123L, 200L, ARRIVAL)));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<Long>> trains = ArgumentCaptor.forClass(Collection.class);
        final ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        final ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findObservedTracks(from.capture(), to.capture(), trains.capture());

        assertEquals(Set.of(TRAIN, 123L), Set.copyOf(trains.getValue()));
        final LocalDate today = DateProvider.dateInHelsinki();
        assertEquals(today.minusDays(HISTORY_DAYS), from.getValue());
        assertEquals(today.minusDays(1), to.getValue());
    }

    private void observed(final TrackObservation... observations) {
        when(repository.findObservedTracks(any(), any(), anyCollection())).thenReturn(List.of(observations));
    }

    private static ZonedDateTime daysAgo(final int days) {
        return DateProvider.dateInHelsinki().minusDays(days).atTime(8, 0).atZone(ZoneId.of("Europe/Helsinki"));
    }

    private static TrackObservation observation(final long attapId, final String station, final String track,
            final int daysBack) {
        return new TrackObservation(TRAIN, attapId, station, ARRIVAL, track, daysAgo(daysBack));
    }
}
