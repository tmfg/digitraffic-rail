package fi.livi.rata.avoindata.server.factory;

import java.time.LocalDate;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.dao.routeset.RoutesectionRepository;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class RoutesetFactory {

    @Autowired
    private RoutesetRepository routesetRepository;

    @Autowired
    private RoutesectionRepository routesectionRepository;

    private static long routesetId = 1;
    private static long routesectionId = 1;

    public Routeset create() {
        Routeset routeset = new Routeset();
        routeset.trainId = new StringTrainId("1", LocalDate.of(2019, 1, 1));
        routeset.id = routesetId++;
        routeset.version = 1L;
        routeset.messageTime = DateProvider.nowInHelsinki();
        routeset.clientSystem = "TEST_C";
        routeset.messageId = "123";
        routeset.routeType = "T";
        routeset.virtualDepartureDate = routeset.trainId.departureDate;

        routeset = routesetRepository.save(routeset);

        routeset.routesections = new ArrayList<>();

        routeset.routesections.add(createRouteSection(routeset, "TC_1", 1));
        routeset.routesections.add(createRouteSection(routeset, "TC_2", 2));
        routeset.routesections.add(createRouteSection(routeset, "TC_3", 3));
        routeset.routesections.add(createRouteSection(routeset, "TC_4", 4));

        return routeset;
    }

    private Routesection createRouteSection(final Routeset routeset, final String commercialTrackId, final int order) {
        final Routesection routesection = new Routesection();
        routesection.commercialTrackId = commercialTrackId;
        routesection.routeset = routeset;
        routesection.id = routesectionId++;
        routesection.stationCode = "STA";
        routesection.sectionOrder = order;
        routesection.sectionId = String.format("%s_%s", routesection.stationCode, commercialTrackId);

        return routesectionRepository.save(routesection);
    }
}
