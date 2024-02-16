package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrainLocation;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrip;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSTripService.TRIP_REPLACEMENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeedMessageServiceTest extends BaseTest {
    private static final LocalDate DATE_1 = LocalDate.of(2022, 1, 1);
    private static final LocalDate DATE_2 = LocalDate.of(2022, 1, 10);

    private static final String TRIP_ID_1 = "trip_1";
    private static final String TRIP_ID_2 = TRIP_ID_1 + TRIP_REPLACEMENT;

    private static final String ROUTE_ID_1 = "route_1";

    private static final GTFSTrip TRIP_1 = new GTFSTrip(1L, DATE_1, DATE_2, TRIP_ID_1, ROUTE_ID_1, 1);
    private static final GTFSTrip TRIP_2 = new GTFSTrip(1L, DATE_1, DATE_2, TRIP_ID_2, ROUTE_ID_1, 1);

    private static GeometryFactory geometryFactory = new GeometryFactory();

    @MockBean
    private GTFSTripRepository gtfsTripRepository;

    @Autowired
    private FeedMessageService feedMessageService;

    private static GTFSTrainLocation createTrainLocation(final Long trainNumber, final LocalDate departureDate) {
        final GTFSTrainLocation location = mock(GTFSTrainLocation.class);

        when(location.getTrainNumber()).thenReturn(trainNumber);
        when(location.getDepartureDate()).thenReturn(departureDate);
        when(location.getX()).thenReturn(60.0);
        when(location.getY()).thenReturn(20.0);

        return location;
    }

    @Test
    public void empty() {
        when(gtfsTripRepository.findAll()).thenReturn(Collections.emptyList());

        final GtfsRealtime.FeedMessage message = feedMessageService.createVehicleLocationFeedMessage(Collections.emptyList());

        Assertions.assertNotNull(message.getHeader());
        Assertions.assertNotNull(message.getEntityList());
        Assertions.assertEquals(0, message.getEntityCount());
    }

    @Test
    public void oneTrip() {
        final GTFSTrainLocation location = createTrainLocation(1L, DATE_1);
        when(gtfsTripRepository.findAll()).thenReturn(List.of(TRIP_1));

        final GtfsRealtime.FeedMessage message = feedMessageService.createVehicleLocationFeedMessage(List.of(location));

        Assertions.assertEquals(1, message.getEntityCount());
        Assertions.assertEquals(TRIP_ID_1, message.getEntity(0).getVehicle().getTrip().getTripId());
        Assertions.assertEquals(ROUTE_ID_1, message.getEntity(0).getVehicle().getTrip().getRouteId());
    }

    @Test
    public void wrongDepartureDate() {
        final GTFSTrainLocation location = createTrainLocation(1L, DATE_1.minusDays(100));
        when(gtfsTripRepository.findAll()).thenReturn(List.of(TRIP_1));

        final GtfsRealtime.FeedMessage message = feedMessageService.createVehicleLocationFeedMessage(List.of(location));

        Assertions.assertEquals(0, message.getEntityCount());
    }

    @Test
    public void replacement() {
        final GTFSTrainLocation location = createTrainLocation(1L, DATE_1);
        when(gtfsTripRepository.findAll()).thenReturn(List.of(TRIP_1, TRIP_2));

        final GtfsRealtime.FeedMessage message = feedMessageService.createVehicleLocationFeedMessage(List.of(location));

        Assertions.assertEquals(1, message.getEntityCount());
        Assertions.assertEquals(TRIP_ID_2, message.getEntity(0).getVehicle().getTrip().getTripId());
        Assertions.assertEquals(ROUTE_ID_1, message.getEntity(0).getVehicle().getTrip().getRouteId());
    }
}

