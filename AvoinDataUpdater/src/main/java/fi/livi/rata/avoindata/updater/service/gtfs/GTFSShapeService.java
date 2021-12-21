package fi.livi.rata.avoindata.updater.service.gtfs;

import static org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.osgeo.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Shape;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

@Service
public class GTFSShapeService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrakediaRouteService trakediaRouteService;

    @Autowired
    private TrakediaLiikennepaikkaService liikennepaikkaService;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    public List<Shape> createShapesFromTrips(List<Trip> trips, Map<String, Stop> stopMap) {
        ZonedDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC);
        Map<String, JsonNode> trakediaNodes = liikennepaikkaService.getTrakediaLiikennepaikkaNodes(startOfDay);

        Map<String, List<Shape>> shapeCache = new HashMap<>();
        for (Trip trip : trips) {
            log.info("Creating shape for trip {}", trip.tripId);

            String stops = trip.stopTimes.stream().map(s -> s.stopId).collect(Collectors.joining(">"));

            List<Shape> shapes = shapeCache.get(stops);
            if (shapes == null) {
                shapeCache.put(stops, createShapes(stopMap, trakediaNodes, trip, stops));
            }

            trip.shapeId = stops;
        }
        return shapeCache.values().stream().flatMap(s -> s.stream()).collect(Collectors.toList());
    }

    private List<Shape> createShapes(Map<String, Stop> stopMap, Map<String, JsonNode> trakediaNodes, Trip trip, String stops) {
        List<StopTime> actualStops = getActualStops(trip);

        List<Coordinate> coordinates = getCoordinates(stopMap, trakediaNodes, actualStops);

        List<Shape> tripsShapes = new ArrayList<>();
        for (int i1 = 0; i1 < coordinates.size(); i1++) {
            Coordinate point = coordinates.get(i1);

            ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(point.x, point.y);

            Shape shape = new Shape();
            shape.shapeId = stops;
            shape.longitude = projCoordinate.x;
            shape.latitude = projCoordinate.y;
            shape.sequence = i1;

            tripsShapes.add(shape);
        }
        return tripsShapes;
    }

    private List<StopTime> getActualStops(Trip trip) {
        List<StopTime> actualStops = new ArrayList<>();
        actualStops.add(trip.stopTimes.get(0));
        for (int i = 1; i < trip.stopTimes.size() - 1; i++) {
            StopTime current = trip.stopTimes.get(i);

            if (!current.arrivalTime.equals(current.departureTime)) {
                actualStops.add(current);
            }
        }
        actualStops.add(trip.stopTimes.get(trip.stopTimes.size() - 1));
        return actualStops;
    }

    private List<Coordinate> getCoordinates(Map<String, Stop> stopMap, Map<String, JsonNode> trakediaNodes, List<StopTime> stopTimes) {
        List<Coordinate> tripPoints = new ArrayList<>();
        for (int i = 0; i < stopTimes.size() - 1; i++) {
            StopTime startStopTime = stopTimes.get(i);
            StopTime endStopTime = stopTimes.get(i + 1);

            Stop startStop = stopMap.get(startStopTime.stopId);
            Stop endStop = stopMap.get(endStopTime.stopId);

            try {
                JsonNode startTrakediaNode = trakediaNodes.get(startStop.stopId);
                JsonNode endTrakediaNode = trakediaNodes.get(endStop.stopId);

                String startTunniste = startTrakediaNode.get(0).get("tunniste").textValue();
                String endTunniste = endTrakediaNode.get(0).get("tunniste").textValue();

//                log.info("Creating shape for {} -> {}", startStop.stopCode, endStop.stopCode);
                List<Coordinate> route = this.trakediaRouteService.createRoute(startStop, endStop, startTunniste, endTunniste);
                if (!route.isEmpty()) {
                    tripPoints.addAll(route);
                } else {
                    tripPoints.addAll(createDummyRoute(startStop, endStop));
                }
            } catch (Exception e) {
                log.warn("Creating route failed for {} -> {}", startStop.stopCode, endStop.stopCode, e);
                tripPoints.addAll(createDummyRoute(startStop, endStop));
            }
        }

        return tripPoints;
    }

    private List<Coordinate> createDummyRoute(Stop startStop, Stop endStop) {
        ProjCoordinate start = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        ProjCoordinate end = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);
        List<Coordinate> dummyRoute = List.of(new Coordinate(start.x, start.y), new Coordinate(end.x, end.y));
        return dummyRoute;
    }


}
