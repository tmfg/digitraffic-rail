package fi.livi.rata.avoindata.updater.service.gtfs;

import static org.locationtech.jts.simplify.DouglasPeuckerSimplifier.simplify;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Iterators;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.DijkstraAlgorithm;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.Edge;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.Graph;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.NearestPointsService;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.Vertex;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;

@Service
public class TrakediaRouteService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private NearestPointsService nearestPointsService;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Cacheable("trakediaRoute")
    public List<Coordinate> createRoute(Stop startStop, Stop endStop, String startTunniste, String endTunniste) throws InterruptedException {
//        log.info("Creating route from {} -> {}", startStop.stopCode, endStop.stopCode);

        ZonedDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC);
        String startOfDayIso8601 = startOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        String routeUrl = String.format("https://rata.digitraffic.fi/infra-api/latest/reitit/kaikki/%s/%s.json?propertyName=geometria&time=%s", correctTunniste(startTunniste), correctTunniste(endTunniste), String.format("%s/%s", startOfDayIso8601, startOfDayIso8601));

        log.info(routeUrl);

        JsonNode apiRoute = restTemplate.getForObject(routeUrl, JsonNode.class);


        List<List<Coordinate>> allLines = new ArrayList<>();
        for (JsonNode lineNode : apiRoute.get("geometria")) {
            List<Coordinate> output = new ArrayList<>();
            for (JsonNode pointNode : lineNode) {
                output.add(new Coordinate(pointNode.get(0).asDouble(), pointNode.get(1).asDouble()));
            }
            allLines.add(simplifyCoordinates(output));
        }

        return getShortestPath(allLines, startStop, endStop);
    }

    private List<Coordinate> simplifyCoordinates(List<Coordinate> coordinates) {
        if (coordinates.size() <= 10) {
            return coordinates;
        }
        GeometryFactory geometryFactory = new GeometryFactory();

        CoordinateSequence points = new CoordinateArraySequence(coordinates.toArray(new Coordinate[0]));
        LineString simplifiedGeomerty = (LineString) simplify(new LineString(points, geometryFactory), 2.0);
        List<Coordinate> simplifiedCoordinates = Arrays.asList(simplifiedGeomerty.getCoordinates());
        return simplifiedCoordinates;
    }

    private String correctTunniste(String tunniste) {
        return tunniste.replace("x.x.xxx.LIVI.INFRA.", "1.2.246.586.1.");
    }

    private List<Coordinate> getShortestPath(List<List<Coordinate>> lines, Stop startStop, Stop endStop) {
        DijkstraAlgorithm dijkstraAlgorithm = createDijkstraAlgorithm(lines);
        Map<String, Edge> edgeMap = dijkstraAlgorithm.getEdges().stream().collect(Collectors.toMap(Edge::getId, edge -> edge, (a, b) -> b));

        ProjCoordinate startProjCoordinate = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        ProjCoordinate endProjCoordinate = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);

        Coordinate startStopPoint = new Coordinate(startProjCoordinate.x, startProjCoordinate.y);
        Coordinate endStopPoint = new Coordinate(endProjCoordinate.x, endProjCoordinate.y);

        List<Coordinate> coordinates = lines.stream().flatMap(x -> List.of(x.get(0), x.get(x.size() - 1)).stream()).collect(Collectors.toList());

        List<Coordinate> startPoints = this.nearestPointsService.kClosest(coordinates, startStopPoint, 20);
        List<Coordinate> endPoints = this.nearestPointsService.kClosest(coordinates, endStopPoint, 20);

        for (Coordinate startPoint : startPoints) {
            for (Coordinate endPoint : endPoints) {
                dijkstraAlgorithm.execute(new Vertex(getVertexName(startPoint), getVertexName(startPoint)));
                List<Vertex> path = dijkstraAlgorithm.getPath(new Vertex(getVertexName(endPoint), getVertexName(endPoint)));
                if (path != null) {
                    List<Coordinate> shortestPath = new ArrayList<>();
                    for (int i = 0; i < path.size()-1; i++) {
                        Vertex start = path.get(i);
                        Vertex end = path.get(i + 1);

                        String edgeId = getEdgeId(start.getId(), end.getId());
                        Edge edge = edgeMap.get(edgeId);
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

    private DijkstraAlgorithm createDijkstraAlgorithm(List<List<Coordinate>> lines) {
        Set<Vertex> vertices = new HashSet<>();
        ArrayList<Edge> edges = new ArrayList<>();

        for (List<Coordinate> line : lines) {
            int total = line.size();
            Coordinate startNode = line.get(0);
            Coordinate endNode = line.get(total - 1);

            String startVertexName = getVertexName(startNode);
            String endVertexName = getVertexName(endNode);

            Vertex lineStartVertex = new Vertex(startVertexName, startVertexName);
            Vertex lineEndVertex = new Vertex(endVertexName, endVertexName);

            vertices.add(lineStartVertex);
            vertices.add(lineEndVertex);

            int weight = (int) (distanceBetweenTwoPoints(startNode, endNode) * 100);
            edges.add(new Edge(getEdgeId(startVertexName, endVertexName), lineStartVertex, lineEndVertex, weight, line));

            List<Coordinate> lineReversed = new ArrayList<>(line);
            Collections.reverse(lineReversed);
            edges.add(new Edge(getEdgeId(endVertexName, startVertexName), lineEndVertex, lineStartVertex, weight, lineReversed));
        }

        ArrayList<Vertex> verticeList = new ArrayList<>(vertices);
        Graph graph = new Graph(verticeList, edges);
        DijkstraAlgorithm dijkstraAlgorithm = new DijkstraAlgorithm(graph);
        return dijkstraAlgorithm;
    }

    private String getEdgeId(String startVertexName, String endVertexName) {
        return String.format("%s > %s", startVertexName, endVertexName);
    }

    private String getVertexName(Coordinate node) {
        return String.format("%s_%s", node.x, node.y);
    }

    private double distanceBetweenTwoPoints(Coordinate pointA, Coordinate pointB) {
        return Math.sqrt((pointB.x - pointA.x) * (pointB.x - pointA.x) + (pointB.y - pointA.y) * (pointB.y - pointA.y));
    }
}
