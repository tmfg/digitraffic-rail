package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Shape;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

@Service
public class GTFSShapeService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrakediaRouteService trakediaRouteService;

    @Autowired
    private TrakediaLiikennepaikkaService liikennepaikkaService;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Autowired
    private StoptimesSplitterService stoptimesSplitterService;

    public List<Shape> createShapesFromTrips(final List<Trip> trips, final Map<String, Stop> stopMap) {
        final ZonedDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC);
        final Map<String, JsonNode> trakediaNodes = liikennepaikkaService.getTrakediaLiikennepaikkaNodes();

        final Map<Integer, List<Shape>> shapeCache = new HashMap<>();
        for (final Trip trip : trips) {
            final Integer stops = trip.stopTimes.stream().map(s -> s.stopId).collect(Collectors.joining(">")).hashCode();

            final List<Shape> shapes = shapeCache.get(stops);
            if (shapes == null) {
                shapeCache.put(stops, createShapes(stopMap, trakediaNodes, trip, stops));
            }

            trip.shapeId = stops;
        }
        return shapeCache.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    private List<Shape> createShapes(final Map<String, Stop> stopMap, final Map<String, JsonNode> trakediaNodes, final Trip trip, final int stops) {
        final List<StopTime> actualStops = this.stoptimesSplitterService.splitStoptimes(trip);

        final List<Coordinate> coordinates = getCoordinates(stopMap, trakediaNodes, actualStops, trip);

        final List<Shape> tripsShapes = new ArrayList<>();
        for (int i1 = 0; i1 < coordinates.size(); i1++) {
            final Coordinate point = coordinates.get(i1);

            final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(point.x, point.y);

            final Shape shape = new Shape();
            shape.shapeId = stops;
            shape.longitude = projCoordinate.x;
            shape.latitude = projCoordinate.y;
            shape.sequence = i1;

            tripsShapes.add(shape);
        }
        return tripsShapes;
    }


    private List<Coordinate> getCoordinates(final Map<String, Stop> stopMap, final Map<String, JsonNode> trakediaNodes, final List<StopTime> stopTimes, final Trip trip) {
        final List<Coordinate> tripPoints = new ArrayList<>();
        for (int i = 0; i < stopTimes.size() - 1; i++) {
            final StopTime startStopTime = stopTimes.get(i);
            final StopTime endStopTime = stopTimes.get(i + 1);

            final Stop startStop = stopMap.get(startStopTime.stopId);
            final Stop endStop = stopMap.get(endStopTime.stopId);

            List<Coordinate> route = null;

            try {
                final JsonNode startTrakediaNode = trakediaNodes.get(startStop.stopId);
                final JsonNode endTrakediaNode = trakediaNodes.get(endStop.stopId);

                if (startTrakediaNode == null || startTrakediaNode.size() == 0) {
                    log.warn("No nodes found for {}", startStop.stopId);
                } else if (endTrakediaNode == null || endTrakediaNode.size() == 0) {
                    log.warn("No nodes found for {}", endStop.stopId);
                } else {
                    final String startTunniste = startTrakediaNode.get(0).get("tunniste").textValue();
                    final String endTunniste = endTrakediaNode.get(0).get("tunniste").textValue();

                    route = this.trakediaRouteService.createRoute(startStop, endStop, startTunniste, endTunniste);
                }
            } catch (final HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 503 || e.getStatusCode().value() == 504) {
                    log.warn(String.format("Creating route failed for %s %s -> %s: %s", trip.tripId, startStop.stopCode, endStop.stopCode, stopTimes), e);
                } else {
                    log.error(String.format("Creating route failed for %s %s -> %s: %s %s ", trip.tripId, startStop.stopCode, endStop.stopCode, trip.stopTimes, trip.source.scheduleRows), e);
                }
            } catch (final Exception e) {
                log.error(String.format("Creating route failed for %s %s -> %s: %s %s", trip.tripId, startStop.stopCode, endStop.stopCode, trip.stopTimes, trip.source.scheduleRows), e);
            }

            if (route == null || route.isEmpty()) {
                tripPoints.addAll(createDummyRoute(startStop, endStop));
            } else {
                tripPoints.addAll(route);
            }
        }

        return tripPoints;
    }

    private List<Coordinate> createDummyRoute(final Stop startStop, final Stop endStop) {
        final ProjCoordinate start = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        final ProjCoordinate end = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);
        final List<Coordinate> dummyRoute = List.of(new Coordinate(start.x, start.y), new Coordinate(end.x, end.y));
        return dummyRoute;
    }


}
