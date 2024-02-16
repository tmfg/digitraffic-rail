package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import fi.livi.rata.avoindata.updater.factory.TrainLocationFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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

        t.timeTableRows.forEach(timeTableRow -> {
            timeTableRow.actualTime = null;
        });

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
    public void getTrainLocationsNoActualTime() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));

        assertLocations(locations, 1, null, null);
    }

    @Test
    public void getTrainLocationsFirstHasActualTime() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        t.timeTableRows.get(0).actualTime = t.timeTableRows.get(0).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(0);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }

    @Test
    public void getTrainLocationsOneHasActualTime() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        t.timeTableRows.get(4).actualTime = t.timeTableRows.get(4).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }

    @Test
    public void getTrainLocationsFirstTwoActualTimes() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        t.timeTableRows.get(0).actualTime = t.timeTableRows.get(0).scheduledTime;
        t.timeTableRows.get(4).actualTime = t.timeTableRows.get(4).scheduledTime;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(4);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }

    @Test
    public void getTrainLocationsFirstTwoActualTimesFirstIsCommercial() {
        final Train t = createTrainWithoutActualTimes();
        final TrainLocation tl = trainLocationFactory.create(t);

        // should not include 4th line, because it's not commercial stop
        t.timeTableRows.get(0).actualTime = t.timeTableRows.get(0).scheduledTime;
        t.timeTableRows.get(4).actualTime = t.timeTableRows.get(4).scheduledTime;
        t.timeTableRows.get(4).commercialStop = false;
        trainRepository.save(t);

        final List<GTFSTrainLocation> locations = gtfsTrainRepository.getTrainLocations(List.of(tl.id));
        final TimeTableRow ttr = t.timeTableRows.get(0);

        assertLocations(locations, 1, ttr.station.stationShortCode, ttr.commercialTrack);
    }
}
