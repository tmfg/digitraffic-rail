package fi.livi.rata.avoindata.updater.service.gtfs;

import static fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageService.PAST_LIMIT_MINUTES;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.google.transit.realtime.GtfsRealtime;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageService;

public class FeedMessageServiceTrainUpdateTest  extends BaseTest {
    @Autowired
    private FeedMessageService feedMessageService;

    @MockitoBean
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

                if(!arrival) {
                    tuIndex++;
                }

                arrival = !arrival;
            }
        }
    }

    private void assertStopIds(final GtfsRealtime.FeedMessage message, final int entityIndex, final int stopTimeUpdateCount, final String... stopIds) {
        Assertions.assertEquals(stopTimeUpdateCount, message.getEntityList().get(entityIndex).getTripUpdate().getStopTimeUpdateCount());

        if(stopIds.length > 0) {
            final GtfsRealtime.TripUpdate tu = message.getEntity(entityIndex).getTripUpdate();

            int tuIndex = 0;
            for(final String stopId : stopIds) {
                final GtfsRealtime.TripUpdate.StopTimeUpdate stu = tu.getStopTimeUpdate(tuIndex);
                Assertions.assertEquals(stopId, stu.getStopId(), String.format("Row %d(%s)", tuIndex, stu.getStopId()));

                tuIndex++;
            }
        }
    }

    private void assertAssignedStopId(final GtfsRealtime.FeedMessage message, final int entityIndex, final int stopTimeUpdateCount, final String... stopIds) {
        Assertions.assertEquals(stopTimeUpdateCount, message.getEntityList().get(entityIndex).getTripUpdate().getStopTimeUpdateCount());

        if(stopIds.length > 0) {
            final GtfsRealtime.TripUpdate tu = message.getEntity(entityIndex).getTripUpdate();

            int tuIndex = 0;
            for(final String stopId : stopIds) {
                final GtfsRealtime.TripUpdate.StopTimeUpdate stu = tu.getStopTimeUpdate(tuIndex);

                if(stopId == null) {
                    Assertions.assertFalse(stu.hasStopTimeProperties());
                } else {
                    Assertions.assertEquals(stopId, stu.getStopTimeProperties().getAssignedStopId());
                }

                tuIndex++;
            }
        }
    }

    private void assertHasNoStopId(final GtfsRealtime.FeedMessage message, final int entityIndex, final int stopTimeUpdateIndex) {
        final GtfsRealtime.TripUpdate.StopTimeUpdate stu = message.getEntity(entityIndex).getTripUpdate().getStopTimeUpdate(stopTimeUpdateIndex);
        Assertions.assertFalse(stu.hasStopId(), String.format("Stop time update %d should not have stop_id set", stopTimeUpdateIndex));
    }

    private void assertCancelled(final GtfsRealtime.FeedMessage message, final int entityIndex, final boolean... cancelled) {
        Assertions.assertEquals(cancelled.length, message.getEntityList().get(entityIndex).getTripUpdate().getStopTimeUpdateCount());

        final GtfsRealtime.TripUpdate tu = message.getEntity(entityIndex).getTripUpdate();

        for(int tuIndex = 0; tuIndex < cancelled.length; tuIndex++) {
            final GtfsRealtime.TripUpdate.StopTimeUpdate stu = tu.getStopTimeUpdate(tuIndex);
            final boolean shouldBeCancelled = cancelled[tuIndex];
            final GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship expected = shouldBeCancelled
                    ? GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED
                    : GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED;

            Assertions.assertEquals(expected, stu.getScheduleRelationship(), String.format("Row %d(%s) should be %s, was %s", tuIndex, stu.getStopId(), expected, stu.getScheduleRelationship()));
        };
    }

    private GTFSTimeTableRow getRow(final GTFSTrain train, final int rowNumber, final TimeTableRow.TimeTableRowType type) {
        final GTFSTimeTableRow row = train.timeTableRows.get(rowNumber);

        if(row.type != type) throw new IllegalArgumentException("Wrong rowtype");

        return row;
    }

    private GTFSTrain createTestTrain() {
        // create test train with row estimates
        final GTFSTrain train1 = new GTFSTrainBuilder(new TrainId(1, TEST_DATE_TODAY), LocalTime.now())
                .version(1)
                .departure("S1", 10).rowActualTime(10)
                .arrival("S2", 20).rowActualTime(20)
                .build();
        final GTFSTrip trip1 = createTrip(1, TEST_DATE_TODAY, GTFS_TRIP_VERSION); // 2 > 1

        // and mock repository to return matching GtfsTrip
        when(gtfsTripRepository.findAll()).thenReturn(List.of(trip1));

        return train1;
    }

    private GTFSTrain createTestTrain2() {
        // create test train with row estimates
        final GTFSTrain train1 = new GTFSTrainBuilder(new TrainId(2, TEST_DATE_TODAY), LocalTime.now())
                .version(1)
                .departure("S1", 10).rowActualTime(10)
                .arrival("S2", 20).rowActualTime(11)
                .departure("S2", 22).rowActualTime(10)
                .arrival("S3", 27).rowActualTime(20)
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
        final GTFSTrain train2 = createTestTrain2();

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertStopUpdates(message, 0, 3,
                0, 600, 660, 600, 1200, 0);
    }

    @Test
    public void allRowsCancelled() {
        final GTFSTrain train2 = createTestTrain2();
        train2.timeTableRows.get(0).cancelled= true;
        train2.timeTableRows.get(1).cancelled= true;
        train2.timeTableRows.get(2).cancelled= true;
        train2.timeTableRows.get(3).cancelled= true;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertCancelled(message, 0, true, true, true);
    }

    @Test
    public void firstRowCancelled() {
        final GTFSTrain train2 = createTestTrain2();
        train2.timeTableRows.get(0).cancelled= true;
        train2.timeTableRows.get(1).cancelled= true;
        train2.timeTableRows.get(2).cancelled= false;
        train2.timeTableRows.get(3).cancelled= false;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertCancelled(message, 0, true, false, false);
    }

    @Test
    public void lastRowCancelled() {
        final GTFSTrain train2 = createTestTrain2();
        train2.timeTableRows.get(0).cancelled= false;
        train2.timeTableRows.get(1).cancelled= false;
        train2.timeTableRows.get(2).cancelled= true;
        train2.timeTableRows.get(3).cancelled= true;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        // if last arrival is cancelled, then the following departure is cancelled
        assertCancelled(message, 0, false, true, true);
    }

    @Test
    public void secondDelayIsTheSame() {
        final GTFSTrain train1 = createTestTrain();
        // set same delay for both rows
        train1.timeTableRows.get(0).actualTime = train1.timeTableRows.get(0).scheduledTime.plusMinutes(10);
        train1.timeTableRows.get(1).actualTime = train1.timeTableRows.get(1).scheduledTime.plusMinutes(10);

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        // there should be only one stopUpdate, because the delay is same, so it won't be duplicated
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
    public void estimatesInPastNoActualTime() {
        final GTFSTrain train1 = createTestTrain();
        train1.timeTableRows.get(0).scheduledTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 20);
        train1.timeTableRows.get(0).liveEstimateTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 20);
        train1.timeTableRows.get(0).actualTime = null;
        train1.timeTableRows.get(1).scheduledTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 15);
        train1.timeTableRows.get(1).liveEstimateTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 15);
        train1.timeTableRows.get(1).actualTime = null;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train1));

        assertFeedMessage(message, 0);
    }

    @Test
    public void actualTimeInPast() {
        final GTFSTrain train1 = createTestTrain();
        train1.timeTableRows.get(0).scheduledTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 20);
        train1.timeTableRows.get(0).actualTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 20);
        train1.timeTableRows.get(1).scheduledTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 15);
        train1.timeTableRows.get(1).actualTime = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES + 15);

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

    @Test
    public void departureEstimateBeforeArrivalEstimate() {
        final GTFSTrain train2 = createTestTrain2();
        // arrival has no estimate, but departures estimate is before arrivals scheduled time
        final GTFSTimeTableRow r1a = getRow(train2, 1, TimeTableRow.TimeTableRowType.ARRIVAL);
        final GTFSTimeTableRow r1d = getRow(train2, 2, TimeTableRow.TimeTableRowType.DEPARTURE);

        r1a.liveEstimateTime = r1a.actualTime;
        r1d.actualTime = null;
        r1d.liveEstimateTime = r1a.liveEstimateTime.minusMinutes(1); // departure estimate is 1 minute before arrival estimate

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertStopUpdates(message, 0, 3,
                0, 600, 480, 480, 1200, 0);
        // before 0, 600, 1200, 600, 1200, 0
    }

    @Test
    public void stopIdsDefaultStation() {
        final GTFSTrain train2 = createTestTrain2();
        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertStopIds(message, 0, 3, "S1_0", "S2_0", "S3_0");
    }

    @Test
    public void stopIdsTrack() {
        final GTFSTrain train2 = createTestTrain2();
        // set tracks
        final GTFSTimeTableRow r1d = getRow(train2, 0, TimeTableRow.TimeTableRowType.DEPARTURE);
        final GTFSTimeTableRow r2a = getRow(train2, 1, TimeTableRow.TimeTableRowType.ARRIVAL);

        r1d.commercialTrack = "1";
        r2a.commercialTrack = "2";

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        assertStopIds(message, 0, 3, "S1_1", "S2_2", "S3_0");
        // Normal tracks should not have assigned_stop_id
        assertAssignedStopId(message, 0, 3, null, null, null);
    }

    @Test
    public void unknownTrack() {
        final GTFSTrain train2 = createTestTrain2();
        // set tracks
        final GTFSTimeTableRow r1d = getRow(train2, 0, TimeTableRow.TimeTableRowType.DEPARTURE);
        final GTFSTimeTableRow r2a = getRow(train2, 1, TimeTableRow.TimeTableRowType.ARRIVAL);

        r1d.commercialTrack = "1";
        r1d.unknownTrack = true;
        r2a.commercialTrack = "2";
        r2a.unknownTrack = true;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        // When unknown track is set, stop_id should NOT be set, only assigned_stop_id should be in stop_time_properties
        assertHasNoStopId(message, 0, 0);
        assertHasNoStopId(message, 0, 1);
        assertAssignedStopId(message, 0, 3, "S1_0", "S2_0", null);
    }

    @Test
    public void trackChanged() {
        final GTFSTrain train2 = createTestTrain2();
        // set tracks and mark them as changed
        final GTFSTimeTableRow r1d = getRow(train2, 0, TimeTableRow.TimeTableRowType.DEPARTURE);
        final GTFSTimeTableRow r2a = getRow(train2, 1, TimeTableRow.TimeTableRowType.ARRIVAL);
        final GTFSTimeTableRow r2d = getRow(train2, 2, TimeTableRow.TimeTableRowType.DEPARTURE);

        r1d.commercialTrack = "1";
        r1d.commercialTrackChanged = ZonedDateTime.now();
        r2a.commercialTrack = "2";
        r2a.commercialTrackChanged = ZonedDateTime.now();

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        // When track has changed, stop_id should NOT be set, only assigned_stop_id should be in stop_time_properties
        assertHasNoStopId(message, 0, 0);
        assertHasNoStopId(message, 0, 1);
        assertAssignedStopId(message, 0, 3, "S1_1", "S2_2", null);
    }

    @Test
    public void trackChangedAndUnknownTrack() {
        final GTFSTrain train2 = createTestTrain2();
        // set one track as changed and another as unknown
        final GTFSTimeTableRow r1d = getRow(train2, 0, TimeTableRow.TimeTableRowType.DEPARTURE);
        final GTFSTimeTableRow r2a = getRow(train2, 1, TimeTableRow.TimeTableRowType.ARRIVAL);

        r1d.commercialTrack = "1";
        r1d.commercialTrackChanged = ZonedDateTime.now();
        r2a.commercialTrack = "2";
        r2a.unknownTrack = true;

        final GtfsRealtime.FeedMessage message = feedMessageService.createTripUpdateFeedMessage(List.of(train2));

        assertFeedMessage(message, 1);
        // Both should use assigned_stop_id in stop_time_properties, not stop_id
        assertHasNoStopId(message, 0, 0);
        assertHasNoStopId(message, 0, 1);
        assertAssignedStopId(message, 0, 3, "S1_1", "S2_0", null);
    }
}
