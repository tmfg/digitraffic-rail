package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.*;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import tools.jackson.databind.JsonNode;
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
    private Wgs84ConversionService wgs84ConversionService;

    @Autowired
    private StoptimesSplitterService stoptimesSplitterService;

    public List<Shape> createShapesFromTrips(final List<Trip> trips, final Map<String, Stop> stopMap,
                                             final Map<String, JsonNode> trakediaNodes, final GtfsRunContext context) {
        final GtfsRunMetrics metrics = context.metrics();
        final Map<Integer, List<Shape>> shapeCache = new HashMap<>();
        int processedShapes = 0;
        int realShapes = 0;
        for (final Trip trip : trips) {
            final Integer stops = trip.stopTimes.stream().map(s -> s.stopId).collect(Collectors.joining(">")).hashCode();

            if (!shapeCache.containsKey(stops)) {
                final ShapeGroup group = createShapes(stopMap, trakediaNodes, trip, stops, context);
                shapeCache.put(stops, group.shapes());
                if (group.complete()) {
                    realShapes += group.shapes().size();
                }
                metrics.recordShapeProcessed(context.currentFeed(), ++processedShapes, trips.size())
                        .ifPresent(heartbeat -> log.info("{}", heartbeat));
            }

            trip.shapeId = stops;
        }
        final List<Shape> shapes = shapeCache.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        metrics.recordShapes(shapes.size(), realShapes);
        if (realShapes < shapes.size()) {
            metrics.recordFeedDegraded(context.currentFeed());
        }
        return shapes;
    }

    /** Shapes for one stop sequence; incomplete when any segment fell back to straight-line geometry. */
    private record ShapeGroup(List<Shape> shapes, boolean complete) {
    }

    private ShapeGroup createShapes(final Map<String, Stop> stopMap, final Map<String, JsonNode> trakediaNodes,
                                    final Trip trip, final int stops, final GtfsRunContext context) {
        final List<StopTime> actualStops = this.stoptimesSplitterService.splitStoptimes(trip);

        final TripGeometry geometry = getCoordinates(stopMap, trakediaNodes, actualStops, trip, context);

        final List<Shape> tripsShapes = new ArrayList<>();
        for (int i1 = 0; i1 < geometry.coordinates().size(); i1++) {
            final Coordinate point = geometry.coordinates().get(i1);

            final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(point.x, point.y);

            final Shape shape = new Shape();
            shape.shapeId = stops;
            shape.longitude = projCoordinate.x;
            shape.latitude = projCoordinate.y;
            shape.sequence = i1;

            tripsShapes.add(shape);
        }
        return new ShapeGroup(tripsShapes, geometry.complete());
    }


    /** Geometry for one trip; incomplete when any segment fell back to straight-line geometry. */
    private record TripGeometry(List<Coordinate> coordinates, boolean complete) {
    }

    private TripGeometry getCoordinates(final Map<String, Stop> stopMap, final Map<String, JsonNode> trakediaNodes,
                                        final List<StopTime> stopTimes, final Trip trip, final GtfsRunContext context) {
        final List<Coordinate> tripPoints = new ArrayList<>();
        boolean complete = true;
        for (int i = 0; i < stopTimes.size() - 1; i++) {
            final Stop startStop = stopMap.get(stopTimes.get(i).stopId);
            final Stop endStop = stopMap.get(stopTimes.get(i + 1).stopId);

            final SegmentOutcome outcome = resolveSegment(trakediaNodes, startStop, endStop, trip, context);

            if (outcome.reason() == null) {
                context.metrics().recordRealSegment();
                tripPoints.addAll(outcome.coordinates());
            } else {
                context.metrics().recordDummySegment(outcome.reason(), startStop.stopCode);
                tripPoints.addAll(createDummyRoute(startStop, endStop));
                complete = false;
            }
        }

        return new TripGeometry(tripPoints, complete);
    }

    /** Coordinates for one stop-to-stop segment, or the reason straight-line geometry must be used. */
    private record SegmentOutcome(List<Coordinate> coordinates, DummyReason reason) {
        private static SegmentOutcome resolved(final List<Coordinate> coordinates) {
            return new SegmentOutcome(coordinates, null);
        }

        private static SegmentOutcome fallback(final DummyReason reason) {
            return new SegmentOutcome(List.of(), reason);
        }
    }

    private SegmentOutcome resolveSegment(final Map<String, JsonNode> trakediaNodes, final Stop startStop,
                                          final Stop endStop, final Trip trip, final GtfsRunContext context) {
        final String segment = startStop.stopId + "->" + endStop.stopId;
        try {
            final JsonNode startTrakediaNode = trakediaNodes.get(startStop.stopId);
            final JsonNode endTrakediaNode = trakediaNodes.get(endStop.stopId);

            if (startTrakediaNode == null || startTrakediaNode.size() == 0) {
                return SegmentOutcome.fallback(DummyReason.NO_START_NODE);
            }
            if (endTrakediaNode == null || endTrakediaNode.size() == 0) {
                return SegmentOutcome.fallback(DummyReason.NO_END_NODE);
            }

            final RouteWorkPolicy.Decision decision = context.routePolicy().decide(segment);
            if (!decision.proceed()) {
                return SegmentOutcome.fallback(decision.dummyReason());
            }

            final TrakediaRouteService.RouteResult result = this.trakediaRouteService.createRoute(startStop, endStop,
                    startTrakediaNode.get(0).get("tunniste").textValue(),
                    endTrakediaNode.get(0).get("tunniste").textValue());
            context.metrics().recordRouteLookup();

            return result.fallbackReason() == null
                    ? SegmentOutcome.resolved(result.coordinates())
                    : SegmentOutcome.fallback(result.fallbackReason());
        } catch (final WebClientResponseException e) {
            context.routePolicy().recordFailure(segment);
            context.metrics().recordRouteFailure(segment,
                    e.getRequest() == null ? "" : String.valueOf(e.getRequest().getURI()),
                    e.getStatusCode().value(), e.getClass().getSimpleName());
            return SegmentOutcome.fallback(DummyReason.ROUTE_HTTP_ERROR);
        } catch (final Exception e) {
            context.routePolicy().recordFailure(segment);
            log.error("method=resolveSegment tripId={} startStop={} endStop={} error.type={}", trip.tripId,
                    startStop.stopCode, endStop.stopCode, e.getClass().getSimpleName(), e);
            return SegmentOutcome.fallback(DummyReason.ROUTE_PROCESSING_ERROR);
        }
    }

    private List<Coordinate> createDummyRoute(final Stop startStop, final Stop endStop) {
        final ProjCoordinate start = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        final ProjCoordinate end = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);
        final List<Coordinate> dummyRoute = List.of(new Coordinate(start.x, start.y), new Coordinate(end.x, end.y));
        return dummyRoute;
    }


}
