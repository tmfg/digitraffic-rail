package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

class IdentityTrackSourceTest {

    private final IdentityTrackSource source = new IdentityTrackSource();

    // --- commuter trains: key on line code + direction, with (line, station) fallback ---

    @Test
    void givenSameLineAndDirection_whenStopHasNoTrack_thenBorrowsFromSibling() {
        final Schedule known = commuter("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule blank = commuter("E", List.of("HKI", "PSL", "KIL"), Arrays.asList(null, null, null));

        final int filled = source.fill(List.of(List.of(known, blank)));

        assertEquals(3, filled);
        assertEquals(List.of("14", "8", "1"), tracksOf(blank));
    }

    /** Direction is preferred: a stop takes its own direction's platform, not the other way's. */
    @Test
    void givenBothDirectionsHaveTracks_whenStopHasNoTrack_thenPrefersOwnDirection() {
        final Schedule outbound = commuter("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule inbound = commuter("E", List.of("KIL", "PSL", "HKI"), List.of("1", "3", "15"));
        final Schedule blank = commuter("E", List.of("KIL", "PSL", "HKI"), Arrays.asList(null, null, null));

        source.fill(List.of(List.of(outbound, inbound, blank)));

        assertEquals("3", blank.scheduleRows.get(1).commercialTrack);
    }

    /** Fallback: with no same-direction sibling, it borrows the (line, station) modal anyway. */
    @Test
    void givenOnlyOppositeDirectionHasTrack_whenStopHasNoTrack_thenFallsBackToLineStation() {
        final Schedule outbound = commuter("E", List.of("HKI", "PSL", "KIL"), List.of("14", "8", "1"));
        final Schedule inbound = commuter("E", List.of("KIL", "PSL", "HKI"), Arrays.asList(null, null, null));

        source.fill(List.of(List.of(outbound, inbound)));

        assertEquals("8", inbound.scheduleRows.get(1).commercialTrack);
    }

    /** The exact next stop no longer matters: a skip-stop variant of the same direction still borrows. */
    @Test
    void givenSkipStopVariantSameDirection_whenStopHasNoTrack_thenBorrows() {
        final Schedule full = commuter("E", List.of("HKI", "PSL", "KVH", "KIL"), List.of("2", "8", "5", "1"));
        final Schedule skips = commuter("E", List.of("HKI", "PSL", "KIL"), Arrays.asList(null, null, null));

        source.fill(List.of(List.of(full, skips)));

        assertEquals("8", skips.scheduleRows.get(1).commercialTrack);
    }

    /** Cross-line last resort: a station's platform known only from another line is borrowed, same direction. */
    @Test
    void givenStationResolvedByAnotherLine_whenThisLineHasNone_thenBorrowsCrossLine() {
        final Schedule otherLine = commuter("K", List.of("HKI", "PSL", "MÄK"), List.of("1", "2", "3"));
        final Schedule blank = commuter("E", List.of("HKI", "PSL", "MÄK"), Arrays.asList(null, null, null));

        source.fill(List.of(List.of(otherLine, blank)));

        assertEquals("3", blank.scheduleRows.get(2).commercialTrack);
    }

    @Test
    void givenTwoPlatformsSeen_whenBorrowing_thenTakesTheMostUsed() {
        final Schedule a = commuter("K", List.of("HKI", "KE"), List.of("5", "2"));
        final Schedule b = commuter("K", List.of("HKI", "KE"), List.of("5", "2"));
        final Schedule c = commuter("K", List.of("HKI", "KE"), List.of("6", "2"));
        final Schedule blank = commuter("K", List.of("HKI", "KE"), Arrays.asList(null, null));

        source.fill(List.of(List.of(a, b, c, blank)));

        assertEquals("5", blank.scheduleRows.get(0).commercialTrack);
    }

    // --- long-distance trains: key on train type + number, no neighbour ---

    @Test
    void givenSameTypeAndNumber_whenStopHasNoTrack_thenBorrows() {
        final Schedule known = longDistance("IC", 1, List.of("HKI", "TPE", "OL"), List.of("3", "2", "1"));
        final Schedule blank = longDistance("IC", 1, List.of("HKI", "TPE", "OL"), Arrays.asList(null, null, null));

        final int filled = source.fill(List.of(List.of(known, blank)));

        assertEquals(3, filled);
        assertEquals(List.of("3", "2", "1"), tracksOf(blank));
    }

    /** Type + number is a single service, so the neighbour is not part of the key and a skip-stop
     *  variant still borrows the platforms it shares. */
    @Test
    void givenSkipStopVariant_whenBorrowing_thenNeighbourIsNotRequired() {
        final Schedule full = longDistance("IC", 1, List.of("HKI", "TPE", "OL"), List.of("3", "2", "1"));
        final Schedule skips = longDistance("IC", 1, List.of("HKI", "OL"), Arrays.asList(null, null));

        final int filled = source.fill(List.of(List.of(full, skips)));

        assertEquals(2, filled);
        assertEquals(List.of("3", "1"), tracksOf(skips));
    }

    @Test
    void givenDifferentNumber_whenFilling_thenNotBorrowed() {
        final Schedule known = longDistance("IC", 1, List.of("HKI", "TPE"), List.of("3", "2"));
        final Schedule other = longDistance("IC", 2, List.of("HKI", "TPE"), Arrays.asList(null, null));

        assertEquals(0, source.fill(List.of(List.of(known, other))));
        assertNull(other.scheduleRows.get(0).commercialTrack);
    }

    @Test
    void givenSameNumberDifferentType_whenFilling_thenNotBorrowed() {
        final Schedule ic = longDistance("IC", 1, List.of("HKI", "TPE"), List.of("3", "2"));
        final Schedule pyo = longDistance("PYO", 1, List.of("HKI", "TPE"), Arrays.asList(null, null));

        assertEquals(0, source.fill(List.of(List.of(ic, pyo))));
        assertNull(pyo.scheduleRows.get(0).commercialTrack);
    }

    // --- trains with neither identity are left alone ---

    @Test
    void givenNoLineIdAndNoType_whenFilling_thenSkipped() {
        final Schedule known = longDistance("IC", 1, List.of("HKI", "TPE"), List.of("3", "2"));
        final Schedule unidentified = build(null, null, null, List.of("HKI", "TPE"), Arrays.asList(null, null));

        assertEquals(0, source.fill(List.of(List.of(known, unidentified))));
        assertNull(unidentified.scheduleRows.get(0).commercialTrack);
    }

    private static List<String> tracksOf(final Schedule schedule) {
        return schedule.scheduleRows.stream().map(r -> r.commercialTrack).toList();
    }

    private static Schedule commuter(final String lineId, final List<String> stations, final List<String> tracks) {
        return build(lineId, null, null, stations, tracks);
    }

    private static Schedule longDistance(final String type, final long number, final List<String> stations,
            final List<String> tracks) {
        return build(null, type, number, stations, tracks);
    }

    private static Schedule build(final String commuterLineId, final String trainType, final Long trainNumber,
            final List<String> stations, final List<String> tracks) {
        final Schedule schedule = new Schedule();
        schedule.commuterLineId = commuterLineId;
        schedule.trainType = trainType == null ? null : new TrainType(trainType);
        schedule.trainNumber = trainNumber;
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
