package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TimeTableRowFactory;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import fi.livi.rata.avoindata.updater.factory.TrainLocationFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCollection;

@Transactional
public class GTFSTrainRepositoryTest extends BaseTest {
    @Autowired
    private GTFSTrainRepository gtfsTrainRepository;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainLocationFactory trainLocationFactory;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Autowired
    private TimeTableRowFactory ttrf;

    private Train createTrainWithoutActualTimes() {
        final Train t = trainFactory.createBaseTrain();

        t.timeTableRows.forEach(timeTableRow -> timeTableRow.actualTime = null);

        trainRepository.save(t);

        return t;
    }

    private void assertLocations(final List<GTFSTrainLocation> locations, final int expectedSize, final String... expectedValues) {
        assertThatCollection(locations).hasSize(expectedSize);

        int index = 0;
        for(final String e: expectedValues) {
            final GTFSTrainLocation tl = locations.get(index / 2);

            if(index % 2 == 0) {
                assertThat(tl.getStationShortCode()).isEqualTo(e);
            } else {
                assertThat(tl.getCommercialTrack()).isEqualTo(e);
            }

            index++;
        }
    }
    @Test
    public void getTrainLocationsNoTrains() {
        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(Collections.emptyList());

        assertLocations(locations, 0);
    }

    @Test
    public void getTrainLocationsNoEstimates() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertLocations(locations, 1, null, null);
    }

    @Test
    public void getTrainLocationsGetFirstWithEstimate() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        t.timeTableRows.getFirst().liveEstimateTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.getFirst();

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
        // Row 0 (HKI DEPARTURE) is the origin - it has no paired ARRIVAL row at all, so the train not yet
        // having departed still counts as "at stop" from the very start.
        assertThat(locations.getFirst().getVehicleAtStop()).isTrue();
    }

    // Regression coverage for the "normal" dwelling case at a non-terminus station: the selected DEPARTURE's
    // paired ARRIVAL at the same station has actually happened (actual_time set), so vehicle_at_stop must be
    // true - this is the main case the coalesce(...)/paired-ARRIVAL subquery exists for.
    @Test
    public void getTrainLocationsDwellingAtIntermediateStop() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // Train has departed HKI and arrived at PSL (rows 0 and 1), and is now dwelling at PSL awaiting
        // departure (row 2): its live estimate is in the future and actual_time is still null.
        t.timeTableRows.getFirst().actualTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.get(1).actualTime = t.timeTableRows.get(1).scheduledTime;
        t.timeTableRows.get(2).liveEstimateTime = t.timeTableRows.get(2).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(2);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
        assertThat(locations.getFirst().getVehicleAtStop()).isTrue();
    }

    // Regression coverage for the newly-added unknownDelay column: it must be read straight off the selected
    // row (bypassing TimeTableRow.getLiveEstimateTime()'s JSON-serialization suppression, which does not apply
    // to this native query) and exposed unchanged as GTFSTrainLocation.getUnknownDelay().
    @Test
    public void getTrainLocationsExposesUnknownDelayFlag() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        t.timeTableRows.getFirst().liveEstimateTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.getFirst().unknownDelay = true;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertThat(locations.getFirst().getUnknownDelay()).isTrue();
    }

    // Regression test for a bug where vehicle_at_stop was derived solely from the selected row's type: here
    // the earlier TPE ARRIVAL is excluded only because its live estimate is stale (still in the past), not
    // because it actually happened (actual_time is still null) - so the later TPE DEPARTURE gets selected
    // instead, but the train has NOT actually arrived at TPE yet. vehicle_at_stop must stay false.
    @Test
    public void getTrainLocationsGetFirstWithEstimateInTheFuture() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // 1 minute in the past should be enough, but is not! some local timezone issue?
        t.timeTableRows.getFirst().liveEstimateTime = ZonedDateTime.now().minusMinutes(300);
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
        assertThat(locations.getFirst().getVehicleAtStop()).isFalse();
    }

    @Test
    public void getTrainLocationsSkipCommercial() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // should not include 1st row, because it's not commercial stop
        t.timeTableRows.getFirst().liveEstimateTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        t.timeTableRows.getFirst().commercialStop = false;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }

    @Test
    public void getTrainLocationsApproachingTerminus() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // All stops before the terminus (last row, an ARRIVAL-only OL) are already completed; the terminus
        // ARRIVAL itself is still pending with a live estimate - the train is approaching, not yet there.
        for (int i = 0; i < 7; i++) {
            t.timeTableRows.get(i).actualTime = t.timeTableRows.get(i).scheduledTime;
        }
        t.timeTableRows.get(7).liveEstimateTime = t.timeTableRows.get(7).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertLocations(locations, 1, "OL", "1");
        assertThat(locations.getFirst().getVehicleAtStop()).isFalse();
    }

    @Test
    public void getTrainLocationsArrivedAtTerminusResolvesNoStop() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // Train has arrived at (and is dwelling at) the terminus: every row, including the terminus ARRIVAL,
        // now has an actual time. A terminus has no DEPARTURE row, so the "next unresolved row" query has
        // nothing left to match - it deliberately returns no stop for this case (see the query's own javadoc):
        // reporting the already-arrived terminus is SIRI-VM-only and resolved separately in Java from the
        // train's full row list (see CommercialStopVisitsTest#resolveTerminusFallback_returnsArrivedTerminus and
        // SiriVmGenerationServiceTest's terminus-fallback wiring coverage).
        for (final TimeTableRow row : t.timeTableRows) {
            row.actualTime = row.scheduledTime;
        }
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertLocations(locations, 1, null, null);
        assertThat(locations.getFirst().getVehicleAtStop()).isNull();
    }

    // Regression test for the vehicle_at_stop window function's own tiebreak (as opposed to the outer
    // "next stop" selection tiebreak covered by getTrainLocationsTieAtStationBoundaryPrefersDeparture below):
    // PSL's own ARRIVAL and DEPARTURE (rows 1-2) are forced to share the exact same scheduled_time (a
    // zero-dwell stop - see CommercialStopVisits/EtJourneyInterpreter's own tie-break handling for the same,
    // real-if-rare occurrence). The train is still approaching PSL (neither row has happened yet), so
    // PSL's DEPARTURE - selected as "next" because the outer tiebreak also prefers DEPARTURE - must NOT be
    // reported as vehicle_at_stop=true: without a deterministic secondary sort key in the window functions
    // that compute vehicle_at_stop, MySQL is free to rank the not-yet-arrived DEPARTURE first within its
    // own same-scheduled_time station partition, wrongly satisfying the "no earlier row -> at stop" branch.
    @Test
    public void getTrainLocationsZeroDwellTieDoesNotWronglyReportAtStop() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // Train has departed HKI (row 0); PSL's ARRIVAL and DEPARTURE (rows 1-2) now share one scheduled_time
        // and are both still pending, with a live estimate at that shared instant.
        t.timeTableRows.getFirst().actualTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.get(2).scheduledTime = t.timeTableRows.get(1).scheduledTime;
        t.timeTableRows.get(1).liveEstimateTime = t.timeTableRows.get(1).scheduledTime;
        t.timeTableRows.get(2).liveEstimateTime = t.timeTableRows.get(2).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(2);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
        // The train has not actually reached PSL yet (its ARRIVAL has not happened) - so despite PSL's
        // DEPARTURE being the resolved "next" row, vehicle_at_stop must be false.
        assertThat(locations.getFirst().getVehicleAtStop()).isFalse();
    }

    // Regression test: TPE's DEPARTURE (row 4) and JY's ARRIVAL (row 5) share the exact same scheduled_time
    // (zero scheduled transit time between adjacent stops - a real TrainFactory fixture occurrence, not
    // contrived). If a same-instant tie were broken ARRIVAL-first, JY's ARRIVAL would be selected as the
    // "next" stop instead of TPE's DEPARTURE, prematurely advancing the reported stop to JY before the train
    // has actually left TPE. DEPARTURE must win the tie.
    @Test
    public void getTrainLocationsTieAtStationBoundaryPrefersDeparture() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // Train has departed HKI, PSL and arrived at TPE (rows 0-3 actual); TPE's DEPARTURE and JY's ARRIVAL
        // (rows 4-5) are both still pending with a live estimate at their shared scheduled_time.
        for (int i = 0; i < 4; i++) {
            t.timeTableRows.get(i).actualTime = t.timeTableRows.get(i).scheduledTime;
        }
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        t.timeTableRows.get(5).liveEstimateTime = t.timeTableRows.get(5).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
        // TPE's own paired ARRIVAL (row 3) already happened, so the train is dwelling at TPE.
        assertThat(locations.getFirst().getVehicleAtStop()).isTrue();
    }

    // Regression test: a cancelled row must be excluded from `next`'s own candidate set even when its live
    // estimate is set in the future - mirrors the existing commercial_stop=false skip test, but for the
    // `tr.cancelled is false` filter instead. Without this filter a cancelled HKI DEPARTURE would wrongly be
    // reported as the current/next stop instead of skipping ahead to PSL's (non-cancelled) ARRIVAL.
    @Test
    public void getTrainLocationsCancelledRowIsSkipped() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        t.timeTableRows.getFirst().liveEstimateTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.getFirst().cancelled = true;
        t.timeTableRows.get(1).liveEstimateTime = t.timeTableRows.get(1).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(1);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }

    // Regression test documenting current behavior: the paired-ARRIVAL subquery filters `arr.cancelled is
    // false`, so a cancelled ARRIVAL row is invisible to it - exactly as if no ARRIVAL row existed at all for
    // that station. A selected DEPARTURE whose only same-station ARRIVAL was cancelled therefore falls into the
    // "no paired ARRIVAL -> origin-like -> at stop" branch (vehicle_at_stop = true), the same as a real origin
    // station. If this coalesce fallback is ever narrowed to only apply at true origins, this test must change
    // together with it - it exists to make that behavior change deliberate rather than accidental.
    @Test
    public void getTrainLocationsCancelledArrivalTreatedAsNoPairedArrival() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // HKI DEPARTURE already happened; PSL's ARRIVAL (row 1) was cancelled (never actually recorded as
        // happened); PSL's DEPARTURE (row 2) is the next not-yet-happened commercial row with a future estimate.
        t.timeTableRows.getFirst().actualTime = t.timeTableRows.getFirst().scheduledTime;
        t.timeTableRows.get(1).cancelled = true;
        t.timeTableRows.get(2).liveEstimateTime = t.timeTableRows.get(2).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(2);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
        assertThat(locations.getFirst().getVehicleAtStop()).isTrue();
    }

    // Regression test: getTrainLocations is always called with a batch of ids (one per live train_location) -
    // each lateral join is correlated on tl.departure_date/train_number, so per-train results must not leak
    // into each other. Proves two different trains resolved in the SAME call each get their own, independently
    // correct next-stop and vehicle_at_stop - not, say, the other train's row, or a cross-joined duplicate.
    @Test
    public void getTrainLocationsBatchOfMultipleTrainsResolvesIndependently() {
        final Train dwellingTrain = createTrainWithoutActualTimes();
        final TrainLocation dwellingLocation = trainLocationFactory.create(dwellingTrain);
        // Dwelling at PSL: HKI departed, PSL ARRIVAL happened, PSL DEPARTURE pending with a future estimate.
        dwellingTrain.timeTableRows.getFirst().actualTime = dwellingTrain.timeTableRows.getFirst().scheduledTime;
        dwellingTrain.timeTableRows.get(1).actualTime = dwellingTrain.timeTableRows.get(1).scheduledTime;
        dwellingTrain.timeTableRows.get(2).liveEstimateTime = dwellingTrain.timeTableRows.get(2).scheduledTime;
        trainRepository.save(dwellingTrain);

        final Train approachingTrain = trainFactory.createBaseTrain(new TrainId(52L, LocalDate.now()));
        approachingTrain.timeTableRows.forEach(row -> row.actualTime = null);
        final TrainLocation approachingLocation = trainLocationFactory.create(approachingTrain);
        // Still approaching HKI (its very first row): nothing has happened yet, only HKI's own estimate is set.
        approachingTrain.timeTableRows.getFirst().liveEstimateTime = approachingTrain.timeTableRows.getFirst().scheduledTime;
        trainRepository.save(approachingTrain);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository
                .getTrainLocations(List.of(dwellingLocation.id, approachingLocation.id));

        assertThatCollection(locations).hasSize(2);
        final GTFSTrainLocation dwellingResult = locations.stream().filter(l -> l.getId() == dwellingLocation.id).findFirst()
                .orElseThrow();
        final GTFSTrainLocation approachingResult = locations.stream().filter(l -> l.getId() == approachingLocation.id).findFirst()
                .orElseThrow();

        assertThat(dwellingResult.getStationShortCode()).isEqualTo("PSL");
        assertThat(dwellingResult.getVehicleAtStop()).isTrue();
        assertThat(approachingResult.getStationShortCode()).isEqualTo("HKI");
        assertThat(approachingResult.getVehicleAtStop()).isTrue(); // HKI is the origin, no paired ARRIVAL exists
    }

    // Regression test for a repeated (loop-line) station visit: the paired-ARRIVAL subquery must match the
    // occurrence closest to (at or before) the selected DEPARTURE's own scheduled_time - not an earlier visit to
    // the same station. AAA is visited twice: the first visit is long completed (both actual times set); the
    // train is now dwelling at AAA's SECOND visit, whose own ARRIVAL has happened but whose DEPARTURE has not.
    // The first visit's ARRIVAL is deliberately left WITHOUT an actual_time (simulating stale/incomplete data
    // for an old visit) so that, if the subquery wrongly matched it instead of the second visit's ARRIVAL,
    // vehicle_at_stop would flip to false - proving the "nearest occurrence" semantics are load-bearing, not
    // incidental.
    @Test
    public void getTrainLocationsRepeatedStationPicksNearestArrivalOccurrence() {
        final TrainId id = new TrainId(53L, LocalDate.now());
        Train t = new Train(id.trainNumber, id.departureDate, 1, "test", 1L, 1L, "Z", true, false, 1L,
                Train.TimetableType.REGULAR, ZonedDateTime.now());
        t = trainRepository.save(t);

        final ZonedDateTime base = ZonedDateTime.now().plusHours(1);
        final List<TimeTableRow> rows = new ArrayList<>();
        rows.add(ttrf.create(t, base, base, new StationEmbeddable("HKI", 1, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE));
        // First AAA visit: ARRIVAL deliberately left without actual_time (simulated stale data - see class
        // comment on this test).
        rows.add(ttrf.create(t, base.plusHours(1), null, new StationEmbeddable("AAA", 9, "FI"), TimeTableRow.TimeTableRowType.ARRIVAL));
        rows.add(ttrf.create(t, base.plusHours(1).plusMinutes(1), base.plusHours(1).plusMinutes(1),
                new StationEmbeddable("AAA", 9, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE));
        rows.add(ttrf.create(t, base.plusHours(2), base.plusHours(2), new StationEmbeddable("BBB", 10, "FI"),
                TimeTableRow.TimeTableRowType.ARRIVAL));
        rows.add(ttrf.create(t, base.plusHours(2).plusMinutes(1), base.plusHours(2).plusMinutes(1),
                new StationEmbeddable("BBB", 10, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE));
        // Second AAA visit: ARRIVAL has actually happened; DEPARTURE is the pending row with a future estimate.
        rows.add(ttrf.create(t, base.plusHours(3), base.plusHours(3), new StationEmbeddable("AAA", 9, "FI"),
                TimeTableRow.TimeTableRowType.ARRIVAL));
        final TimeTableRow secondDeparture = ttrf.create(t, base.plusHours(3).plusMinutes(1), null,
                new StationEmbeddable("AAA", 9, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE);
        secondDeparture.liveEstimateTime = secondDeparture.scheduledTime;
        rows.add(secondDeparture);
        rows.add(ttrf.create(t, base.plusHours(4), null, new StationEmbeddable("OL", 5, "FI"), TimeTableRow.TimeTableRowType.ARRIVAL));

        t.timeTableRows = timeTableRowRepository.saveAll(rows);
        final TrainLocation tl = trainLocationFactory.create(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertThat(locations.getFirst().getStationShortCode()).isEqualTo("AAA");
        assertThat(locations.getFirst().getVehicleAtStop()).isTrue();
    }

    // Regression test for a repeated (loop-line) station visit whose own ARRIVAL is cancelled: the paired-ARRIVAL
    // check must still skip past the cancelled occurrence and find the earlier visits ARRIVAL, exactly like the
    // single-visit cancelled-ARRIVAL case above - not stop at (or wrongly consult) whatever row happens to
    // immediately precede it once the cancelled one is excluded. AAA is visited twice: the first visit is fully
    // completed; the second visits ARRIVAL is cancelled, and its DEPARTURE (still pending) is the candidate row.
    // Between the two visits, the first visits own DEPARTURE is deliberately left pending too (simulated
    // stale/incomplete data), so that a position-based "immediately preceding row" check (rather than a
    // type-filtered "nearest preceding ARRIVAL" one) would wrongly land on it instead of skipping back to the
    // first visits ARRIVAL - proving the type-filtered skip-back is load-bearing, not incidental.
    @Test
    public void getTrainLocationsRepeatedStationSkipsCancelledArrivalOccurrence() {
        final TrainId id = new TrainId(54L, LocalDate.now());
        Train t = new Train(id.trainNumber, id.departureDate, 1, "test", 1L, 1L, "Z", true, false, 1L,
                Train.TimetableType.REGULAR, ZonedDateTime.now());
        t = trainRepository.save(t);

        final ZonedDateTime base = ZonedDateTime.now().plusHours(1);
        final List<TimeTableRow> rows = new ArrayList<>();
        rows.add(ttrf.create(t, base, base, new StationEmbeddable("HKI", 1, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE));
        // First AAA visit: ARRIVAL has happened; DEPARTURE deliberately left pending (simulated stale data -
        // see class comment on this test) so it cannot be mistaken for a completed ARRIVAL by a position-based
        // (rather than type-filtered) check.
        rows.add(ttrf.create(t, base.plusHours(1), base.plusHours(1), new StationEmbeddable("AAA", 9, "FI"),
                TimeTableRow.TimeTableRowType.ARRIVAL));
        final TimeTableRow firstDeparture = ttrf.create(t, base.plusHours(1).plusMinutes(1), null,
                new StationEmbeddable("AAA", 9, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE);
        firstDeparture.liveEstimateTime = ZonedDateTime.now().minusDays(1); // stale: clearly in the past, excludes it as a candidate
        rows.add(firstDeparture);
        // Second AAA visit: ARRIVAL is cancelled (never happens, no actual_time); DEPARTURE is the pending
        // candidate row with a future estimate.
        final TimeTableRow secondArrival = ttrf.create(t, base.plusHours(1).plusMinutes(2), null,
                new StationEmbeddable("AAA", 9, "FI"), TimeTableRow.TimeTableRowType.ARRIVAL);
        secondArrival.cancelled = true;
        rows.add(secondArrival);
        final TimeTableRow secondDeparture = ttrf.create(t, base.plusHours(1).plusMinutes(3), null,
                new StationEmbeddable("AAA", 9, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE);
        secondDeparture.liveEstimateTime = secondDeparture.scheduledTime;
        rows.add(secondDeparture);
        rows.add(ttrf.create(t, base.plusHours(2), null, new StationEmbeddable("OL", 5, "FI"), TimeTableRow.TimeTableRowType.ARRIVAL));

        t.timeTableRows = timeTableRowRepository.saveAll(rows);
        final TrainLocation tl = trainLocationFactory.create(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertThat(locations.getFirst().getStationShortCode()).isEqualTo("AAA");
        // The first visits ARRIVAL (the nearest non-cancelled one) has actually happened, so the train is at
        // stop - even though the row immediately preceding the candidate (once the cancelled ARRIVAL is
        // excluded) is that same first visits still-pending DEPARTURE.
        assertThat(locations.getFirst().getVehicleAtStop()).isTrue();
    }

    /** SIRI-ET fetches live trains by the composite (train_number, departure_date) ids the published NeTEx
     * refers to. Proves MySQL executes the row-value tuple IN at real-time operating-day scale (~200 ids)
     * and returns exactly the requested, sourced trains. */
    @Test
    public void findBySourceVersionAndIdInReturnsRequestedIdsAtOperatingDayScale() {
        final LocalDate today = LocalDate.now();
        final Train wanted1 = createSourcedTrain(new TrainId(9001L, today));
        final Train wanted2 = createSourcedTrain(new TrainId(9002L, today));
        createSourcedTrain(new TrainId(9003L, today)); // exists but not requested → must be excluded

        final List<TrainId> ids = new ArrayList<>();
        ids.add(wanted1.id);
        ids.add(wanted2.id);
        for (long n = 10_000; n < 10_198; n++) { // ~200 ids total, the rest non-existent
            ids.add(new TrainId(n, today));
        }

        final List<GTFSTrain> result = gtfsTrainRepository.findBySourceVersionAndIdIn(0L, ids);

        assertThatCollection(result).extracting(gtfsTrain -> gtfsTrain.id)
                .containsExactlyInAnyOrder(wanted1.id, wanted2.id);
    }

    private Train createSourcedTrain(final TrainId id) {
        final Train train = trainFactory.createBaseTrain(id);
        train.sourceVersion = 1L; // stamped from payload version in production; the query keeps sourceVersion > 0
        return trainRepository.save(train);
    }
}
