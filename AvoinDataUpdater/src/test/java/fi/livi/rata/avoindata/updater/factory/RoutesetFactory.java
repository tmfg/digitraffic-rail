package fi.livi.rata.avoindata.updater.factory;

import java.time.LocalDate;
import java.util.ArrayList;

import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class RoutesetFactory {

    public Routeset create() {
        final Routeset routeset = new Routeset();
        routeset.trainId = new StringTrainId("1", LocalDate.of(2019, 1, 1));
        routeset.id = 1L;
        routeset.version = 1L;
        routeset.messageTime = DateProvider.nowInHelsinki();
        routeset.clientSystem = "TEST_CLIENT";
        routeset.messageId = "123";
        routeset.routeType = "T";
        routeset.virtualDepartureDate = routeset.trainId.departureDate;

        routeset.routesections = new ArrayList<>();

        routeset.routesections.add(createRouteSection(routeset, "TRACK_1", 1));
        routeset.routesections.add(createRouteSection(routeset, "TRACK_2", 2));
        routeset.routesections.add(createRouteSection(routeset, "TRACK_3", 3));
        routeset.routesections.add(createRouteSection(routeset, "TRACK_4", 4));

        return routeset;
    }

    private Routesection createRouteSection(final Routeset routeset, final String commercialTrackId, final int order) {
        final Routesection routesection = new Routesection();
        routesection.commercialTrackId = commercialTrackId;
        routesection.routeset = routeset;
        routesection.id = 2L;
        routesection.stationCode = "TEST_STATION";
        routesection.sectionOrder = order;
        return routesection;
    }
}
