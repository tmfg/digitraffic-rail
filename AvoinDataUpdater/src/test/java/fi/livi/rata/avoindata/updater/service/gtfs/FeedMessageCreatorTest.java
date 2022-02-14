package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageCreator;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSTripService.TRIP_REPLACEMENT;

public class FeedMessageCreatorTest {
    private static final LocalDate DATE_1 = LocalDate.of(2022, 1, 1);
    private static final LocalDate DATE_2 = LocalDate.of(2022, 1, 10);

    private static final String TRIP_ID_1 = "trip_1";
    private static final String TRIP_ID_2 = TRIP_ID_1 + TRIP_REPLACEMENT;

    private static final String ROUTE_ID_1 = "route_1";

    private static final GTFSTrip TRIP_1 = new GTFSTrip(1L, DATE_1, DATE_2, TRIP_ID_1, ROUTE_ID_1, 1);
    private static final GTFSTrip TRIP_2 = new GTFSTrip(1L, DATE_1, DATE_2, TRIP_ID_2, ROUTE_ID_1, 1);

    private static GeometryFactory geometryFactory = new GeometryFactory();

    private static TrainLocation createTrainLocation(final Long trainNumber, final LocalDate departureDate) {
        final TrainLocation location = new TrainLocation();

        location.id = 1L;
        location.location = geometryFactory.createPoint(new Coordinate(60, 20));
        location.speed = 100;
        location.trainLocationId = new TrainLocationId(trainNumber, departureDate, ZonedDateTime.now());

        return location;
    }

    @Test
    public void empty() {
        final FeedMessageCreator creator = new FeedMessageCreator(Collections.emptyList());
        final GtfsRealtime.FeedMessage message = creator.createVehicleLocationFeedMessage(Collections.emptyList());

        Assert.assertNotNull(message.getHeader());
        Assert.assertNotNull(message.getEntityList());
        Assert.assertEquals(0, message.getEntityCount());
    }

    @Test
    public void oneTrip() {
        final TrainLocation location = createTrainLocation(1L, DATE_1);

        final FeedMessageCreator creator = new FeedMessageCreator(List.of(TRIP_1));
        final GtfsRealtime.FeedMessage message = creator.createVehicleLocationFeedMessage(List.of(location));

        Assert.assertEquals(1, message.getEntityCount());
        Assert.assertEquals(TRIP_ID_1, message.getEntity(0).getVehicle().getTrip().getTripId());
        Assert.assertEquals(ROUTE_ID_1, message.getEntity(0).getVehicle().getTrip().getRouteId());
    }

    @Test
    public void wrongDepartureDate() {
        final TrainLocation location = createTrainLocation(1L, DATE_1.minusDays(100));

        final FeedMessageCreator creator = new FeedMessageCreator(List.of(TRIP_1));
        final GtfsRealtime.FeedMessage message = creator.createVehicleLocationFeedMessage(List.of(location));

        Assert.assertEquals(0, message.getEntityCount());
    }

    @Test
    public void replacement() {
        final TrainLocation location = createTrainLocation(1L, DATE_1);

        final FeedMessageCreator creator = new FeedMessageCreator(List.of(TRIP_1, TRIP_2));
        final GtfsRealtime.FeedMessage message = creator.createVehicleLocationFeedMessage(List.of(location));

        Assert.assertEquals(1, message.getEntityCount());
        Assert.assertEquals(TRIP_ID_2, message.getEntity(0).getVehicle().getTrip().getTripId());
        Assert.assertEquals(ROUTE_ID_1, message.getEntity(0).getVehicle().getTrip().getRouteId());
    }
}

