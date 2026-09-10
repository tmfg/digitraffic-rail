package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

class LineTrackSourceTest {

    private final LineTrackSource source = new LineTrackSource();

    @Test
    void givenSameLineAndDirection_whenStopHasNoTrack_thenBorrowsFromSibling() {
        final Schedule known = line("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule blank = line("E", List.of("HKI", "PSL", "KIL"), Arrays.asList(null, null, null));

        final int filled = source.fill(List.of(List.of(known, blank)));

        assertEquals(3, filled);
        assertEquals("14", blank.scheduleRows.get(0).commercialTrack);
        assertEquals("8", blank.scheduleRows.get(1).commercialTrack);
        assertEquals("1", blank.scheduleRows.get(2).commercialTrack);
    }

    /** The platform a line uses at a station depends on which way it is going. */
    @Test
    void givenOppositeDirection_whenStopHasNoTrack_thenDoesNotBorrowAcrossDirections() {
        final Schedule outbound = line("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule inbound = line("E", List.of("KIL", "PSL", "HKI"), Arrays.asList(null, null, null));

        source.fill(List.of(List.of(outbound, inbound)));

        assertNull(inbound.scheduleRows.get(1).commercialTrack);
    }

    @Test
    void givenTerminus_whenBorrowing_thenDoesNotTakeThroughStopPlatform() {
        // PSL as an end point, arriving from HKI, must not take the platform used when passing through to KIL
        final Schedule through = line("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule terminating = line("E", List.of("HKI", "PSL"), Arrays.asList(null, null));

        source.fill(List.of(List.of(through, terminating)));

        assertNull(terminating.scheduleRows.get(1).commercialTrack);
    }

    @Test
    void givenLongDistanceSchedule_whenFilling_thenLeftAlone() {
        final Schedule known = line("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule longDistance = line(null, List.of("HKI", "PSL", "KIL"), Arrays.asList(null, null, null));

        final int filled = source.fill(List.of(List.of(known, longDistance)));

        assertEquals(0, filled);
        assertNull(longDistance.scheduleRows.get(1).commercialTrack);
    }

    @Test
    void givenTwoPlatformsSeen_whenBorrowing_thenTakesTheMostUsed() {
        final Schedule a = line("K", List.of("HKI", "KE"), List.of("5", "2"));
        final Schedule b = line("K", List.of("HKI", "KE"), List.of("5", "2"));
        final Schedule c = line("K", List.of("HKI", "KE"), List.of("6", "2"));
        final Schedule blank = line("K", List.of("HKI", "KE"), Arrays.asList(null, null));

        source.fill(List.of(List.of(a, b, c, blank)));

        assertEquals("5", blank.scheduleRows.get(0).commercialTrack);
    }

    private static Schedule line(final String commuterLineId, final List<String> stations,
            final List<String> tracks) {
        final Schedule schedule = new Schedule();
        schedule.commuterLineId = commuterLineId;
        schedule.scheduleRows = new ArrayList<>();
        long id = 1;
        for (int i = 0; i < stations.size(); i++) {
            final ScheduleRow row = new ScheduleRow();
            row.id = id++;
            row.station = new StationEmbeddable(stations.get(i), 100 + i, "FI");
            row.commercialTrack = tracks.get(i);
            if (i > 0) {
                row.arrival = part(id++, row);
            }
            if (i < stations.size() - 1) {
                row.departure = part(id++, row);
            }
            schedule.scheduleRows.add(row);
        }
        return schedule;
    }

    private static ScheduleRowPart part(final long id, final ScheduleRow row) {
        final ScheduleRowPart p = new ScheduleRowPart();
        p.id = id;
        p.timestamp = Duration.ofMinutes(id);
        p.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        p.scheduleRow = row;
        return p;
    }
}
