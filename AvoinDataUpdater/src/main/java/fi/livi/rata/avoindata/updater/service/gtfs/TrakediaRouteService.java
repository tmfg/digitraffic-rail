package fi.livi.rata.avoindata.updater.service.gtfs;

import tools.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.observability.GtfsRunScope;
import fi.livi.rata.avoindata.updater.service.gtfs.observability.RouteMetricsSink;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.*;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.proj4j.ProjCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.retry.support.RetryTemplate;
import fi.livi.rata.avoindata.updater.config.InfraApiRetry;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify;

@Service
public class TrakediaRouteService {
    static final String CACHE_NAME = "trakediaRoute";

    private final RetryTemplate retryTemplate = InfraApiRetry.create();
    private Set<String> ignoredStations = Set.of("PYE");
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WebClient webClient;

    @Autowired
    private NearestPointsService nearestPointsService;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    // Stop has no equals/hashCode, so the default key generator falls back to identity and never
    // hits across feeds, which rebuild their Stop objects. The tunniste pair plus the route date is
    // the real identity; the date matters because the response is scoped by the time parameter.
    // Empty results are excluded: one transient empty response must not outlive the run that saw
    // it. Spring unwraps the Optional before evaluating unless, so #result is the list or null.
    @Cacheable(cacheNames = CACHE_NAME, key = "#routeDate + '|' + #startTunniste + '>' + #endTunniste",
            unless = "#result == null")
    public Optional<List<Coordinate>> createRoute(final Stop startStop, final Stop endStop, final String startTunniste,
                                                  final String endTunniste, final LocalDate routeDate) throws InterruptedException {
        final ZonedDateTime startOfDay = routeDate.atStartOfDay(ZoneOffset.UTC);
        final String startOfDayIso8601 = startOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        final String timeParameter = String.format("%s/%s", startOfDayIso8601, startOfDayIso8601);

        final String routeUrl = String.format(
                "https://rata.digitraffic.fi/infra-api/latest/reitit/kaikki/%s/%s.json?propertyName=geometria&time=%s&jatkokerroin=1",
                correctTunniste(startTunniste), correctTunniste(endTunniste), timeParameter);

        final JsonNode apiRoute = retryTemplate.execute(context -> webClient.get().uri(routeUrl).retrieve().bodyToMono(JsonNode.class).block());

        final List<List<Coordinate>> allLines = new ArrayList<>();
        final JsonNode geometria = apiRoute.get("geometria");
        if (geometria.isEmpty()){
            if (!ignoredStations.contains(startStop.stopId) && !ignoredStations.contains(endStop.stopId)) {
                log.warn("Trakedia returned 0 size geometry for {}->{} ({})", startStop.stopCode, endStop.stopCode, routeUrl);
            }
            GtfsRunScope.routeMetrics().recordRouteOutcome(RouteMetricsSink.RouteOutcome.EMPTY_GEOMETRY);
            return Optional.empty();
        }
        for (final JsonNode lineNode : geometria) {
            final List<Coordinate> output = new ArrayList<>();
            for (final JsonNode pointNode : lineNode) {
                output.add(new Coordinate(pointNode.get(0).asDouble(), pointNode.get(1).asDouble()));
            }
            allLines.add(simplifyCoordinates(output));
        }

        final List<Coordinate> shortestPath = getShortestPath(allLines, startStop, endStop);
        if (shortestPath.isEmpty()) {
            GtfsRunScope.routeMetrics().recordRouteOutcome(RouteMetricsSink.RouteOutcome.NO_PATH);
            return Optional.empty();
        }
        GtfsRunScope.routeMetrics().recordRouteOutcome(RouteMetricsSink.RouteOutcome.RESOLVED);
        return Optional.of(shortestPath);
    }

    private List<Coordinate> simplifyCoordinates(final List<Coordinate> coordinates) {
        if (coordinates.size() <= 10) {
            return coordinates;
        }
        final GeometryFactory geometryFactory = new GeometryFactory();

        final CoordinateSequence points = new CoordinateArraySequence(coordinates.toArray(new Coordinate[0]));
        final LineString simplifiedGeomerty = (LineString) simplify(new LineString(points, geometryFactory), 2.0);
        final List<Coordinate> simplifiedCoordinates = Arrays.asList(simplifiedGeomerty.getCoordinates());
        return simplifiedCoordinates;
    }

    private String correctTunniste(final String tunniste) {
        return tunniste.replace("x.x.xxx.LIVI.INFRA.", "1.2.246.586.1.");
    }

    private List<Coordinate> getShortestPath(final List<List<Coordinate>> lines, final Stop startStop, final Stop endStop) {
        final DijkstraAlgorithm dijkstraAlgorithm = createDijkstraAlgorithm(lines);
        final Map<String, Edge> edgeMap = dijkstraAlgorithm.getEdges().stream().collect(Collectors.toMap(Edge::getId, edge -> edge, (a, b) -> b));

        final ProjCoordinate startProjCoordinate = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        final ProjCoordinate endProjCoordinate = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);

        final Coordinate startStopPoint = new Coordinate(startProjCoordinate.x, startProjCoordinate.y);
        final Coordinate endStopPoint = new Coordinate(endProjCoordinate.x, endProjCoordinate.y);

        final List<Coordinate> coordinates = lines.stream().flatMap(Collection::stream).collect(Collectors.toList());

        final List<Coordinate> startPoints = this.nearestPointsService.kClosest(coordinates, startStopPoint, 20);
        final List<Coordinate> endPoints = this.nearestPointsService.kClosest(coordinates, endStopPoint, 20);

        for (final Coordinate startPoint : startPoints) {
            for (final Coordinate endPoint : endPoints) {
                dijkstraAlgorithm.execute(new Vertex(getVertexName(startPoint), getVertexName(startPoint)));
                final List<Vertex> path = dijkstraAlgorithm.getPath(new Vertex(getVertexName(endPoint), getVertexName(endPoint)));
                if (path != null) {
                    final List<Coordinate> shortestPath = new ArrayList<>();
                    for (int i = 0; i < path.size()-1; i++) {
                        final Vertex start = path.get(i);
                        final Vertex end = path.get(i + 1);

                        final String edgeId = getEdgeId(start.getId(), end.getId());
                        final Edge edge = edgeMap.get(edgeId);
                        if (edge == null) {
                            log.error(String.format("Edge not found: %s", edgeId));
                        }
                        else {
                            shortestPath.addAll(edge.getCoordinates());
                        }
                    }
                    return shortestPath;
                }
            }
        }

        log.warn("Could not find Dijkstra path for {} -> {}", startStop.stopCode, endStop.stopCode);
        return new ArrayList<>();
    }

    private DijkstraAlgorithm createDijkstraAlgorithm(final List<List<Coordinate>> lines) {
        final Set<Vertex> vertices = new HashSet<>();
        final ArrayList<Edge> edges = new ArrayList<>();

        for (final List<Coordinate> line : lines) {
            for (int i = 0; i < line.size()-1; i++) {
                final Coordinate startNode = line.get(i);
                final Coordinate endNode = line.get(i+1);

                final String startVertexName = getVertexName(startNode);
                final String endVertexName = getVertexName(endNode);

                final Vertex lineStartVertex = new Vertex(startVertexName, startVertexName);
                final Vertex lineEndVertex = new Vertex(endVertexName, endVertexName);

                vertices.add(lineStartVertex);
                vertices.add(lineEndVertex);

                final int weight = (int) (distanceBetweenTwoPoints(startNode, endNode) * 100);
                edges.add(new Edge(getEdgeId(startVertexName, endVertexName), lineStartVertex, lineEndVertex, weight, List.of(startNode, endNode)));

                final List<Coordinate> lineReversed = new ArrayList<>(line);
                Collections.reverse(lineReversed);
                edges.add(new Edge(getEdgeId(endVertexName, startVertexName), lineEndVertex, lineStartVertex, weight, List.of(endNode, startNode)));
            }
        }

        final ArrayList<Vertex> verticeList = new ArrayList<>(vertices);
        final Graph graph = new Graph(verticeList, edges);
        final DijkstraAlgorithm dijkstraAlgorithm = new DijkstraAlgorithm(graph);
        return dijkstraAlgorithm;
    }

    private String getEdgeId(final String startVertexName, final String endVertexName) {
        return String.format("%s > %s", startVertexName, endVertexName);
    }

    private String getVertexName(final Coordinate node) {
        return String.format("%s_%s", node.x, node.y);
    }

    private double distanceBetweenTwoPoints(final Coordinate pointA, final Coordinate pointB) {
        return Math.sqrt((pointB.x - pointA.x) * (pointB.x - pointA.x) + (pointB.y - pointA.y) * (pointB.y - pointA.y));
    }
}
