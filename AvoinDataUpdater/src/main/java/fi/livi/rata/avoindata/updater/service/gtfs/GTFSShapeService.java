package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.osgeo.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

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

    @Autowired
    private StoptimesSplitterService stoptimesSplitterService;

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
        List<StopTime> actualStops = this.stoptimesSplitterService.splitStoptimes(trip);

        List<Coordinate> coordinates = getCoordinates(stopMap, trakediaNodes, actualStops, trip);

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


    private List<Coordinate> getCoordinates(Map<String, Stop> stopMap, Map<String, JsonNode> trakediaNodes, List<StopTime> stopTimes, Trip trip) {
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
            } catch (HttpClientErrorException e) {
                if (e.getRawStatusCode() == 400) {
                    log.warn(String.format("Creating route failed for %s %s -> %s: %s", trip.tripId, startStop.stopCode, endStop.stopCode, stopTimes), e);
                    tripPoints.addAll(createDummyRoute(startStop, endStop));
                } else {
                    log.error(String.format("Creating route failed for %s %s -> %s: %s", trip.tripId,startStop.stopCode, endStop.stopCode, trip.tripId), e);
                    tripPoints.addAll(createDummyRoute(startStop, endStop));
                }
            } catch (Exception e) {
                log.error(String.format("Creating route failed for %s %s -> %s: %s", trip.tripId, startStop.stopCode, endStop.stopCode, trip.tripId), e);
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
