package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageService.PAST_LIMIT_MINUTES;
import static org.mockito.Mockito.when;

public class FeedMessageServiceTrainUpdateTest  extends BaseTest {
    @Autowired
    private FeedMessageService feedMessageService;

    @MockBean
    private GTFSTripRepository gtfsTripRepository;

    private final LocalDate TEST_DATE_TODAY = LocalDate.now();
    private final Long GTFS_TRIP_VERSION = 2L;

    private GTFSTrip createTrip(final long trainNumber, final LocalDate date, final long version) {
        return new GTFSTrip(trainNumber, date, date, "trip", "route", version);
    }

    private void assertFeedMessage(final GtfsRealtime.FeedMessage message, final int entityCount) {
        Assertions.assertEquals(entityCount, message.getEntityCount());
    }

    private void assertStopUpdates(final GtfsRealtime.FeedMessage message, final int entityIndex, final int stopTimeUpdateCount, final int... delays) {
        Assertions.assertEquals(stopTimeUpdateCount, message.getEntityList().get(entityIndex).getTripUpdate().getStopTimeUpdateCount());

        if(delays.length > 0) {
            final GtfsRealtime.TripUpdate tu = message.getEntity(entityIndex).getTripUpdate();

            int tuIndex = 0;
            boolean arrival = true;
            for(final int delay : delays) {
                final GtfsRealtime.TripUpdate.StopTimeUpdate stu = tu.getStopTimeUpdate(tuIndex);
                if(arrival) {
                    Assertions.assertEquals(delay, stu.getArrival().getDelay(), String.format("Row %d(%s) arrival", tuIndex, stu.getStopId()));
                } else {
                    Assertions.assertEquals(delay, stu.getDeparture().getDelay(), String.format("Row %d(%s) departure", tuIndex, stu.getStopId()));
                }

                if(arrival == false) {
                    tuIndex++;
                }

                arrival = !arrival;
            }
        }
    }

    private GTFSTimeTableRow getRow(final GTFSTrain train, final int rowNumber, final TimeTableRow.TimeTableRowType type) {
        final GTFSTimeTableRow row = train.timeTableRows.get(rowNumber);

        if(row.type != type) throw new IllegalArgumentException("Wrong rowtype");

        return row;
    }

    private GTFSTrain createTestTrain() {
        // create test train with row estimates
        final GTFSTrain train1 = new GTFSTrainBuilder(new TrainId(1, TEST_DATE_TODAY))
                .version(1)
                .departure("S1", 10).rowEstimate(10)
                .arrival("S2", 20).rowEstimate(20)
                .build();
        final GTFSTrip trip1 = createTrip(1, TEST_DATE_TODAY, GTFS_TRIP_VERSION); // 2 > 1

        // and mock repository to return matching GtfsTrip
        when(gtfsTripRepository.findAll()).thenReturn(List.of(trip1));

        return train1;
    }

    private GTFSTrain createTestTrain2() {
        // create test train with row estimates
        final GTFSTrain train1 = new GTFSTrainBuilder(new TrainId(2, TEST_DATE_TODAY))
                .version(1)
                .departure("S1", 10).rowEstimate(10)
                .arrival("S2", 20).rowEstimate(20)
                .departure("S2", 22).rowEstimate(10)
                .arrival("S3", 27).rowEstimate(20)
                .build();
        final GTFSTrip trip1 = createTrip(2, TEST_DATE_TODAY, GTFS_TRIP_VERSION); // 2 > 1

        // and mock repository to return matching GtfsTrip
        when(gtfsTripRepository.findAll()).thenReturn(List.of(trip1));

        return train1;
    }

    @Test
    public void sameVersionNoEntity() {
        final GTFSTrain train1 = createTestTrain();
        train1.version = GTFS_TRIP_VERSION; // set same version as in GtfsTrip

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 0);
    }

    @Test
    public void noRowsNoEntity() {
        final GTFSTrain train1 = createTestTrain();
        train1.timeTableRows.clear();

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 0);
    }

    @Test
    public void createEntity() {
        final GTFSTrain train1 = createTestTrain();

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 1);
        assertStopUpdates(message, 0, 2,
                0, 600, 1200, 0);
    }

    @Test
    public void cancelledRowsNoEntity() {
        final GTFSTrain train1 = createTestTrain();
        train1.timeTableRows.get(0).cancelled= true;
        train1.timeTableRows.get(1).cancelled= true;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 0);
    }

    @Test
    public void secondDelayIsTheSame() {
        final GTFSTrain train1 = createTestTrain();
        // set same delay for both rows
        train1.timeTableRows.get(0).actualTime = train1.timeTableRows.get(0).scheduledTime.plusMinutes(10);
        train1.timeTableRows.get(1).actualTime = train1.timeTableRows.get(1).scheduledTime.plusMinutes(10);

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 1);
        assertStopUpdates(message, 0, 1,
                0, 600);
    }

    @Test
    public void firstStopIsNegative() {
        final GTFSTrain train1 = createTestTrain();
        // set same delay for both rows
        train1.timeTableRows.get(0).actualTime = train1.timeTableRows.get(0).scheduledTime.plusMinutes(-10);
        train1.timeTableRows.get(1).actualTime = train1.timeTableRows.get(1).scheduledTime.plusMinutes(-10);

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 1);
        assertStopUpdates(message, 0, 1,
                -600, -600);
    }

    @Test
    public void noEstimates() {
        final GTFSTrain train1 = createTestTrain();
        train1.timeTableRows.get(0).actualTime = null;
        train1.timeTableRows.get(1).actualTime = null;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 0);
    }

    @Test
    public void estimatesInPast() {
        final GTFSTrain train1 = createTestTrain();
        train1.timeTableRows.get(0).scheduledTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 20);
        train1.timeTableRows.get(0).liveEstimateTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 20);
        train1.timeTableRows.get(1).scheduledTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 15);
        train1.timeTableRows.get(1).liveEstimateTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 15);

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 0);
    }

    @Test
    public void departureEstimateBeforeArrivalSchedule() {
        final GTFSTrain train2 = createTestTrain2();
        // arrival has no estimate, but departures estimate is before arrivals scheduled time
        final GTFSTimeTableRow r1a = getRow(train2, 1, TimeTableRow.TimeTableRowType.ARRIVAL);
        final GTFSTimeTableRow r1d = getRow(train2, 2, TimeTableRow.TimeTableRowType.DEPARTURE);

        r1a.liveEstimateTime = null;
        r1a.actualTime = null;
        r1d.actualTime = r1a.scheduledTime.minusMinutes(2);

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertStopUpdates(message, 0, 3,
                0, 600, -240, -240, 1200, 0);
    }
}
