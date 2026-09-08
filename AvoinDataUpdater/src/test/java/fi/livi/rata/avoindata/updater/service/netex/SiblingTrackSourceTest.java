package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

class SiblingTrackSourceTest {

    private SiblingTrackSource source;

    @BeforeEach
    void setUp() {
        source = new SiblingTrackSource(new NeTExRouteService(new NeTExIdGenerator()));
    }

    @Test
    void givenASiblingOnTheSameRoute_whenFilling_thenItsTrackIsBorrowed() {
        final Schedule donor = schedule(List.of("HKI", "PSL", "LPV"), List.of("5", "10", "4"));
        final Schedule gap = schedule(List.of("HKI", "PSL", "LPV"), Arrays.asList(null, null, null));

        assertEquals(3, source.fill(List.of(List.of(donor, gap))));
        assertEquals(List.of("5", "10", "4"), tracksOf(gap));
    }

    @Test
    void givenSiblingsDisagree_whenFilling_thenTheMostUsedTrackWins() {
        final Schedule a = schedule(List.of("HKI", "LPV"), List.of("9", "4"));
        final Schedule b = schedule(List.of("HKI", "LPV"), List.of("9", "4"));
        final Schedule c = schedule(List.of("HKI", "LPV"), List.of("17", "4"));
        final Schedule gap = schedule(List.of("HKI", "LPV"), Arrays.asList(null, null));

        source.fill(List.of(List.of(a, b, c, gap)));

        assertEquals(List.of("9", "4"), tracksOf(gap));
    }

    @Test
    void givenAnEvenSplit_whenFilling_thenTheChoiceIsStable() {
        final Schedule a = schedule(List.of("HKI", "LPV"), List.of("17", "4"));
        final Schedule b = schedule(List.of("HKI", "LPV"), List.of("9", "4"));
        final Schedule gap = schedule(List.of("HKI", "LPV"), Arrays.asList(null, null));

        source.fill(List.of(List.of(a, b, gap)));
        final List<String> first = tracksOf(gap);

        final Schedule gapAgain = schedule(List.of("HKI", "LPV"), Arrays.asList(null, null));
        source.fill(List.of(List.of(b, a, gapAgain)));

        assertEquals(first, tracksOf(gapAgain));
    }

    @Test
    void givenADifferentRoute_whenFilling_thenItsTrackIsNotBorrowed() {
        final Schedule other = schedule(List.of("HKI", "TPE"), List.of("5", "1"));
        final Schedule gap = schedule(List.of("HKI", "LPV"), Arrays.asList(null, null));

        assertEquals(0, source.fill(List.of(List.of(other, gap))));
        assertNull(gap.scheduleRows.get(0).commercialTrack);
    }

    @Test
    void givenNoSiblingHasATrack_whenFilling_thenNothingIsInvented() {
        final Schedule a = schedule(List.of("HKI", "LPV"), Arrays.asList(null, null));
        final Schedule b = schedule(List.of("HKI", "LPV"), Arrays.asList(null, null));

        assertEquals(0, source.fill(List.of(List.of(a, b))));
    }

    private static List<String> tracksOf(final Schedule schedule) {
        return schedule.scheduleRows.stream().map(r -> r.commercialTrack).toList();
    }

    private static Schedule schedule(final List<String> stations, final List<String> tracks) {
        final Schedule schedule = new Schedule();
        schedule.scheduleRows = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            final ScheduleRow row = new ScheduleRow();
            row.station = new StationEmbeddable(stations.get(i), 100 + i, "FI");
            row.commercialTrack = tracks.get(i);
            if (i > 0) {
                row.arrival = part(row);
            }
            if (i < stations.size() - 1) {
                row.departure = part(row);
            }
            schedule.scheduleRows.add(row);
        }
        return schedule;
    }

    private static ScheduleRowPart part(final ScheduleRow row) {
        final ScheduleRowPart p = new ScheduleRowPart();
        p.timestamp = Duration.ofHours(6);
        p.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        p.scheduleRow = row;
        return p;
    }
}
