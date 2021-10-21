package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.Vertex;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;

@Service
public class TrakediaRouteService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Cacheable("trakediaRoute")
    public List<double[]> createRoute(Stop startStop, Stop endStop, String startTunniste, String endTunniste) {
//        log.info("Creating route from {} -> {}", startStop.stopCode, endStop.stopCode);

        ZonedDateTime startOfDay = LocalDate.now().atStartOfDay(ZoneOffset.UTC);
        String startOfDayIso8601 = startOfDay.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        try {
            String routeUrl = String.format("https://rata.digitraffic.fi/infra-api/latest/reitit/kaikki/%s/%s.json?propertyName=geometria&time=%s", correctTunniste(startTunniste), correctTunniste(endTunniste), String.format("%s/%s", startOfDayIso8601, startOfDayIso8601));

            log.info(routeUrl);

            JsonNode apiRoute = restTemplate.getForObject(routeUrl, JsonNode.class);

            List<Vertex> path = getShortestPath(apiRoute, startStop, endStop);

            List<double[]> output = new ArrayList<>();
            for (int i = 0; i < path.size() - 1; i++) {
                Vertex startVertex = path.get(i);
                Vertex endVertex = path.get(i + 1);

                for (JsonNode lineNode : apiRoute.get("geometria")) {
                    int total = Iterators.size(lineNode.iterator());
                    JsonNode startNode = lineNode.get(0);
                    JsonNode endNode = lineNode.get(total - 1);

                    if (getVertexName(startNode).equals(startVertex.getName()) && getVertexName(endNode).equals(endVertex.getName())) {
                        for (JsonNode pointNode : lineNode) {
                            output.add(new double[]{pointNode.get(0).asDouble(), pointNode.get(1).asDouble()});
                        }
                    } else if (getVertexName(endNode).equals(startVertex.getName()) && getVertexName(startNode).equals(endVertex.getName())) {
                        List<double[]> temp = new ArrayList<>();
                        for (JsonNode pointNode : lineNode) {
                            temp.add(new double[]{pointNode.get(0).asDouble(), pointNode.get(1).asDouble()});
                        }
                        Collections.reverse(temp);
                        output.addAll(temp);
                    }
                }
            }
            return output;
        } catch (Exception e) {
            log.error("Creating route failed for {} -> {}", correctTunniste(startTunniste), correctTunniste(endTunniste), e);
            ProjCoordinate start = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
            ProjCoordinate end = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);
            return List.of(new double[]{start.x, start.y}, new double[]{end.x, end.y});
        }
    }

    private String correctTunniste(String tunniste) {
        return tunniste.replace("x.x.xxx.LIVI.INFRA.", "1.2.246.586.1.");
    }

    private List<Vertex> getShortestPath(JsonNode apiRoute, Stop startStop, Stop endStop) {
        ProjCoordinate startProjCoordinate = wgs84ConversionService.wgs84Tolivi(startStop.longitude, startStop.latitude);
        ProjCoordinate endProjCoordinate = wgs84ConversionService.wgs84Tolivi(endStop.longitude, endStop.latitude);

        double[] startStopPoint = new double[]{startProjCoordinate.x, startProjCoordinate.y};
        double[] endStopPoint = new double[]{endProjCoordinate.x, endProjCoordinate.y};

        Vertex startVertex = null;
        Vertex endVertex = null;
        double minDistanceToStart = Double.MAX_VALUE;
        double minDistanceToEnd = Double.MAX_VALUE;

        Set<Vertex> vertices = new HashSet<>();
        ArrayList<Edge> edges = new ArrayList<>();
        JsonNode geometryNode = apiRoute.get("geometria");
        for (JsonNode lineNode : geometryNode) {
            int total = Iterators.size(lineNode.iterator());
            JsonNode startNode = lineNode.get(0);
            JsonNode endNode = lineNode.get(total - 1);

            String startVertexName = getVertexName(startNode);
            String endVertexName = getVertexName(endNode);

            Vertex lineStartVertex = new Vertex(startVertexName, startVertexName);
            Vertex lineEndVertex = new Vertex(endVertexName, endVertexName);

            vertices.add(lineStartVertex);
            vertices.add(lineEndVertex);

            double[] startPoint = {startNode.get(0).asDouble(), startNode.get(1).asDouble()};
            double[] endPoint = {endNode.get(0).asDouble(), endNode.get(1).asDouble()};

            double startDistanceToStartStop = distanceBetweenTwoPoints(startStopPoint, startPoint);
            double endDistanceToStartStop = distanceBetweenTwoPoints(startStopPoint, endPoint);
            double startDistanceToEndStop = distanceBetweenTwoPoints(endStopPoint, startPoint);
            double endDistanceToEndStop = distanceBetweenTwoPoints(endStopPoint, endPoint);

            if (startDistanceToStartStop < minDistanceToStart) {
                minDistanceToStart = startDistanceToStartStop;
                startVertex = lineStartVertex;
            }
            if (endDistanceToStartStop < minDistanceToStart) {
                minDistanceToStart = endDistanceToStartStop;
                startVertex = lineEndVertex;
            }
            if (startDistanceToEndStop < minDistanceToEnd) {
                minDistanceToEnd = startDistanceToEndStop;
                endVertex = lineStartVertex;
            }
            if (endDistanceToEndStop < minDistanceToEnd) {
                minDistanceToEnd = endDistanceToEndStop;
                endVertex = lineEndVertex;
            }

            int weight = (int) (distanceBetweenTwoPoints(startPoint, endPoint) * 100);
            edges.add(new Edge(String.format("%s > %s", startVertexName, endVertexName), lineStartVertex, lineEndVertex, weight));
            edges.add(new Edge(String.format("%s > %s", endVertexName, startVertexName), lineEndVertex, lineStartVertex, weight));
        }

        ArrayList<Vertex> verticeList = new ArrayList<>(vertices);
        Graph graph = new Graph(verticeList, edges);
        DijkstraAlgorithm dijkstraAlgorithm = new DijkstraAlgorithm(graph);
        dijkstraAlgorithm.execute(startVertex);

        LinkedList<Vertex> path = dijkstraAlgorithm.getPath(endVertex);
        if (path == null) {
//            double minDistance = Double.MAX_VALUE;
//            Vertex minVertex = null;
//            for (Map.Entry<Vertex, Vertex> entry : dijkstraAlgorithm.getPredecessors().entrySet()) {
//                String[] coordinateStrings = entry.getKey().getId().split("_");
//                double x = Double.parseDouble(coordinateStrings[0]);
//                double y = Double.parseDouble(coordinateStrings[1]);
//
//                double distanceBetweenEnds = this.distanceBetweenTwoPoints(new double[]{x, y}, endStopPoint);
//                if (distanceBetweenEnds < minDistance) {
//                    minDistance = distanceBetweenEnds;
//                    minVertex = entry.getKey();
//                }
//            }
//
//            path = dijkstraAlgorithm.getPath(minVertex);

            log.error("Could not find Dijkstra path for {} -> {}", startStop.stopCode, endStop.stopCode);
            return List.of(startVertex, endVertex);
        }
        return path;
    }

    private String getVertexName(JsonNode node) {
        return String.format("%s_%s", node.get(0).asDouble(), node.get(1).asDouble());
    }

    private double distanceBetweenTwoPoints(double[] pointA, double[] pointB) {
        return Math.sqrt((pointB[0] - pointA[0]) * (pointB[0] - pointA[0]) + (pointB[1] - pointA[1]) * (pointB[1] - pointA[1]));
    }
}
