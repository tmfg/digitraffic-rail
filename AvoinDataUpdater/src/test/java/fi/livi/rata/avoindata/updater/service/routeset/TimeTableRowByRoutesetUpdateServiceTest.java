package fi.livi.rata.avoindata.updater.service.routeset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.RoutesetFactory;
import fi.livi.rata.avoindata.updater.factory.TimeTableRowFactory;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;

@Transactional
public class TimeTableRowByRoutesetUpdateServiceTest extends BaseTest {
    @Autowired
    private TimeTableRowByRoutesetUpdateService service;

    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TimeTableRowFactory timeTableRowFactory;

    @Autowired
    private RoutesetFactory routesetFactory;

    @MockBean
    private TrainLockExecutor trainLockExecutor;

    @BeforeEach
    public void setup() {
        //Direct execution because of test transactions
        when(trainLockExecutor.executeInTransactionLock(any())).then(invocationOnMock -> {
            Callable callable = invocationOnMock.getArgument(0);
            return callable.call();
        });
    }

    @Test
    public void oneMatchUpdateShouldWork() {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2019, 1, 1)));
        Routeset routeset = routesetFactory.create();
        routeset.messageTime = train.timeTableRows.get(0).scheduledTime;
        routeset.routesections.get(0).stationCode = "HKI";
        routeset.routesections.get(0).commercialTrackId = "ABC123";

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);
        Assertions.assertEquals("ABC123", returnedTrain.timeTableRows.get(0).commercialTrack);
    }

    @Test
    public void trainWithDoubleStopFirstStopUpdated() {
        Train train = replaceLastRow();

        Routeset routeset = routesetFactory.create();
        routeset.routesections.get(0).stationCode = "HKI";
        routeset.routesections.get(0).commercialTrackId = "UPD";
        routeset.messageTime = train.timeTableRows.get(0).scheduledTime;

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);

        Assertions.assertEquals("HKI", returnedTrain.timeTableRows.get(0).station.stationShortCode);
        Assertions.assertEquals("HKI", Iterables.getLast(returnedTrain.timeTableRows).station.stationShortCode);

        Assertions.assertEquals("UPD", returnedTrain.timeTableRows.get(0).commercialTrack);
        Assertions.assertEquals("LAST", Iterables.getLast(returnedTrain.timeTableRows).commercialTrack);
    }

    @Test
    public void trainWithDoubleStopLastStopUpdated() {
        Train train = replaceLastRow();

        Routeset routeset = routesetFactory.create();
        routeset.routesections.get(0).stationCode = "HKI";
        routeset.routesections.get(0).commercialTrackId = "UPD";
        routeset.messageTime = Iterables.getLast(train.timeTableRows).scheduledTime;

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);
        Assertions.assertEquals("1st", returnedTrain.timeTableRows.get(0).commercialTrack);
        Assertions.assertEquals("UPD", Iterables.getLast(returnedTrain.timeTableRows).commercialTrack);
    }

    @Test
    public void twoConsecutiveTimeTableRowsShouldBeUpdated() {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2019, 1, 1)));
        Routeset routeset = routesetFactory.create();
        routeset.routesections.get(0).stationCode = "TPE";
        routeset.routesections.get(0).commercialTrackId = "UPD";

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);
        Assertions.assertEquals("UPD", returnedTrain.timeTableRows.get(3).commercialTrack);
        Assertions.assertEquals("UPD", returnedTrain.timeTableRows.get(4).commercialTrack);
    }

    @Test
    public void trainWithDoubleStopNearEachOtherShouldNotUpdateBoth() {
        Train train = replaceLastRow();

        ZonedDateTime firstScheduledTime = train.timeTableRows.get(0).scheduledTime;
        for (int i = 0; i < train.timeTableRows.size(); i++) {
            train.timeTableRows.get(i).scheduledTime = firstScheduledTime.plusSeconds(i);
        }

        Routeset routeset = routesetFactory.create();
        routeset.routesections.get(0).stationCode = "HKI";
        routeset.routesections.get(0).commercialTrackId = "UPD";
        routeset.messageTime = Iterables.getLast(train.timeTableRows).scheduledTime;

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);
        Assertions.assertEquals("1st", returnedTrain.timeTableRows.get(0).commercialTrack);
        Assertions.assertEquals("UPD", Iterables.getLast(returnedTrain.timeTableRows).commercialTrack);
    }

    @Test
    public void commercialTrackChangeAfterRoutesetShouldNotUpdate() {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2019, 1, 1)));
        train.timeTableRows.get(0).setCommercialTrackChanged(ZonedDateTime.now());

        Routeset routeset = routesetFactory.create();
        routeset.messageTime = ZonedDateTime.now().minusDays(1);
        routeset.routesections.get(0).stationCode = "HKI";
        routeset.routesections.get(0).commercialTrackId = "UPD";

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);
        Assertions.assertEquals("1", returnedTrain.timeTableRows.get(0).commercialTrack);
    }

    @Test
    public void commercialTrackChangeBeforeRoutesetShouldUpdate() {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2019, 1, 1)));
        train.timeTableRows.get(0).setCommercialTrackChanged(ZonedDateTime.now().minusDays(1));

        Routeset routeset = routesetFactory.create();
        routeset.messageTime = ZonedDateTime.now();
        routeset.routesections.get(0).stationCode = "HKI";
        routeset.routesections.get(0).commercialTrackId = "UPD";

        List<Train> trains = service.updateByRoutesets(Lists.newArrayList(routeset));

        Assertions.assertEquals(1, trains.size());
        Train returnedTrain = trains.get(0);
        Assertions.assertEquals("UPD", returnedTrain.timeTableRows.get(0).commercialTrack);
    }


    private Train replaceLastRow() {
        Train train = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.of(2019, 1, 1)));

        train.timeTableRows.get(0).commercialTrack = "1st";

        TimeTableRow lastStop = Iterables.getLast(train.timeTableRows);
        train.timeTableRows.remove(lastStop);
        TimeTableRow newLastStop = timeTableRowFactory.create(train, lastStop.scheduledTime, lastStop.actualTime, new StationEmbeddable("HKI", 1234, "fi"), lastStop.type);
        newLastStop.commercialTrack = "LAST";
        return train;
    }
}
