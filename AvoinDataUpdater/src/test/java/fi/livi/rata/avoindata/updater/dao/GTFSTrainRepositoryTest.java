package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
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

        t.timeTableRows.get(0).liveEstimateTime = t.timeTableRows.get(0).scheduledTime;
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(0);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }
    @Test
    public void getTrainLocationsGetFirstWithEstimateInTheFuture() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // 1 minute in the past should be enough, but is not! some local timezone issue?
        t.timeTableRows.get(0).liveEstimateTime = ZonedDateTime.now().minusMinutes(300);
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }

    @Test
    public void getTrainLocationsSkipCommercial() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // should not include 1st row, because it's not commercial stop
        t.timeTableRows.get(0).liveEstimateTime = t.timeTableRows.get(0).scheduledTime;
        t.timeTableRows.get(4).liveEstimateTime = t.timeTableRows.get(4).scheduledTime;
        t.timeTableRows.get(0).commercialStop = false;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
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
