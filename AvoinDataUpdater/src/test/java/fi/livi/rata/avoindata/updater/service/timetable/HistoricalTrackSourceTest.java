package fi.livi.rata.avoindata.updater.service.timetable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.gtfs.TimeTableRowService;
import fi.livi.rata.avoindata.updater.service.timetable.HistoricalTrackSource.StopKey;

class HistoricalTrackSourceTest {

    private static final long TRAIN = 59L;
    private static final TimeTableRow.TimeTableRowType ARRIVAL = TimeTableRow.TimeTableRowType.ARRIVAL;

    private TimeTableRowService timeTableRowService;
    private HistoricalTrackSource source;

    @BeforeEach
    void setUp() {
        timeTableRowService = mock(TimeTableRowService.class);
        when(timeTableRowService.getDay(any())).thenReturn(List.of());
        source = new HistoricalTrackSource(timeTableRowService);
        ReflectionTestUtils.setField(source, "historyDays", 14);
    }

    @Test
    void givenNothingWanted_whenResolving_thenNoDayIsRead() {
        assertTrue(source.resolve(Set.of()).isEmpty());
        verify(timeTableRowService, never()).getDay(any());
    }

    @Test
    void givenYesterdayAnswersEverything_whenResolving_thenOlderDaysAreNotRead() {
        onDay(1, row(100L, "HKI", "4", 1, true));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertEquals("4", source.resolve(Set.of(key)).get(key));

        verify(timeTableRowService, times(1)).getDay(any());
    }

    @Test
    void givenTheStopHasNotRunRecently_whenResolving_thenOlderDaysAreRead() {
        onDay(5, row(100L, "HKI", "4", 5, true));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertEquals("4", source.resolve(Set.of(key)).get(key));

        verify(timeTableRowService, times(5)).getDay(any());
    }

    @Test
    void givenNoStopEverAnswers_whenResolving_thenTheWholeWindowIsRead() {
        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertTrue(source.resolve(Set.of(key)).isEmpty());

        verify(timeTableRowService, times(14)).getDay(any());
    }

    @Test
    void givenSeveralDaysHaveTheStop_whenResolving_thenTheMostRecentWins() {
        onDay(1, row(100L, "HKI", "4", 1, true));
        onDay(2, row(100L, "HKI", "9", 2, true));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertEquals("4", source.resolve(Set.of(key)).get(key));
    }

    @Test
    void givenTheStopWasNeverActuallyMade_whenResolving_thenItsTrackIsIgnored() {
        onDay(1, row(100L, "HKI", "4", 1, false));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertNull(source.resolve(Set.of(key)).get(key));
    }

    @Test
    void givenBlankTrack_whenResolving_thenRowIsIgnored() {
        onDay(1, row(100L, "HKI", "", 1, true));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertNull(source.resolve(Set.of(key)).get(key));
    }

    @Test
    void givenOnlyAnotherSchedulePartAtTheStation_whenResolving_thenTheStationKeyAnswers() {
        onDay(1, row(777L, "HKI", "9", 1, true));

        final var stationKey = HistoricalTrackSource.forStation(TRAIN, "HKI", ARRIVAL);
        assertEquals("9", source.resolve(Set.of(stationKey)).get(stationKey));
    }

    @Test
    void givenAnotherTrain_whenResolving_thenNothingIsUsed() {
        final ZonedDateTime when = daysAgo(1);
        onDay(1, new SimpleTimeTableRow(100L, when.toLocalDate(), 999L, "4", when, "HKI", ARRIVAL, when));

        final var key = HistoricalTrackSource.forSchedulePart(TRAIN, 100L, ARRIVAL);
        assertNull(source.resolve(Set.of(key)).get(key));
    }

    private void onDay(final int daysBack, final SimpleTimeTableRow... rows) {
        when(timeTableRowService.getDay(DateProvider.dateInHelsinki().minusDays(daysBack)))
                .thenReturn(List.of(rows));
    }

    private static ZonedDateTime daysAgo(final int days) {
        final LocalDate day = DateProvider.dateInHelsinki().minusDays(days);
        return day.atTime(8, 0).atZone(ZoneId.of("Europe/Helsinki"));
    }

    private static SimpleTimeTableRow row(final long attapId, final String station, final String track,
            final int daysBack, final boolean ran) {
        final ZonedDateTime when = daysAgo(daysBack);
        return new SimpleTimeTableRow(attapId, when.toLocalDate(), TRAIN, track, when, station,
                ARRIVAL, ran ? when : null);
    }
}
