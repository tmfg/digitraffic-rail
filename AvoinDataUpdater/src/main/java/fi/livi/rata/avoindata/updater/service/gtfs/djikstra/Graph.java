package fi.livi.rata.avoindata.updater.service.gtfs.djikstra;

import java.util.List;

public record Graph(List<Vertex> vertexes, List<Edge> edges) {
}
