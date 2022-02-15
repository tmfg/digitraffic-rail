package fi.livi.rata.avoindata.updater.service.gtfs.djikstra;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;

public class Edge  {
    private final String id;
    private final Vertex source;
    private final Vertex destination;
    private final int weight;
    private List<Coordinate> coordinates;

    public Edge(String id, Vertex source, Vertex destination, int weight, List<Coordinate> coordinates) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.weight = weight;
        this.coordinates = coordinates;
    }

    public String getId() {
        return id;
    }
    public Vertex getDestination() {
        return destination;
    }

    public Vertex getSource() {
        return source;
    }
    public int getWeight() {
        return weight;
    }
    public List<Coordinate> getCoordinates() {
        return coordinates;
    }

    @Override
    public String toString() {
        return source + " " + destination;
    }


}
