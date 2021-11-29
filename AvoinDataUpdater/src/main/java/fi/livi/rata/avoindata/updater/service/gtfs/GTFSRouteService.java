package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Route;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class GTFSRouteService {
    @Autowired
    private GTFSTrainTypeService gtfsTrainTypeService;

    public List<Route> createRoutesFromTrips(final List<Trip> trips, final Map<String, Stop> stopMap) {
        Map<String, Route> routeMap = new HashMap<>();

        for (final Trip trip : trips) {
            final Route route = new Route();
            final Schedule schedule = trip.source;

            final StopTime firstStop = trip.stopTimes.get(0);
            final StopTime lastStop = trip.stopTimes.get(trip.stopTimes.size() - 1);

            final String routeId = getRouteId(trip);
            route.routeId = routeId;
            route.agencyId = schedule.operator.operatorUICCode;
            route.longName = String.format("%s - %s", stopMap.get(firstStop.stopId).name, stopMap.get(lastStop.stopId).name);

            if (Strings.isNullOrEmpty(schedule.commuterLineId)) {
                route.shortName = String.format("%s %s", schedule.trainType.name, schedule.trainNumber);
            } else {
                route.shortName = schedule.commuterLineId;
            }

            route.type = gtfsTrainTypeService.getGtfsTrainType(schedule);

            trip.routeId = routeId;

            routeMap.putIfAbsent(route.routeId, route);
        }

        return Lists.newArrayList(routeMap.values());
    }

    private String getRouteId(final Trip trip) {
        final StopTime firstStop = trip.stopTimes.get(0);
        final StopTime lastStop = Iterables.getLast(trip.stopTimes);
        Long id;
        if (!Strings.isNullOrEmpty(trip.source.commuterLineId)) {
            id = Long.valueOf(String.format("%s_%s_%s_%s_%s", firstStop.stopId, lastStop.stopId, trip.source.commuterLineId,
                    gtfsTrainTypeService.getGtfsTrainType(trip.source), trip.source.operator.operatorUICCode).hashCode()) + Integer.MAX_VALUE;
        } else {
            id = Long.valueOf(String.format("%s_%s_%s_%s_%s", firstStop.stopId, lastStop.stopId, trip.source.trainNumber,
                    gtfsTrainTypeService.getGtfsTrainType(trip.source), trip.source.operator.operatorUICCode).hashCode()) + Integer.MAX_VALUE;
        }
        return id.toString();
    }
}
