package fi.livi.rata.avoindata.server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;
import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class GeoJsonFormatter {
    @Autowired
    private ObjectMapper objectMapper;

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    public <E> FeatureCollection wrapAsGeoJson(List<E> entities, Function<E, Double[]> coordinateProvider) {
        return new FeatureCollection(createFeatureCollection(entities, coordinateProvider));
    }

    private <E> List<Feature> createFeatureCollection(List<E> entities, Function<E, Double[]> coordinateProvider) {
        List<Feature> features = new ArrayList<>();
        for (E entity : entities) {
            features.add(createFeature(coordinateProvider, entity));
        }
        return features;
    }

    private <E> Feature createFeature(Function<E, Double[]> coordinateProvider, E entity) {
        Map propertyMap = objectMapper.convertValue(entity, Map.class);
        propertyMap.remove("location");
        propertyMap.remove("longitude");
        propertyMap.remove("latitude");

        Double[] coords = coordinateProvider.apply(entity);

        return new Feature(geometryFactory.createPoint(new Coordinate(coords[0], coords[1])), propertyMap);
    }
}
