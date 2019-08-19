package fi.livi.rata.avoindata.server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;
import fi.livi.rata.avoindata.server.controller.api.geojson.GeoJsonResponse;
import fi.livi.rata.avoindata.server.controller.api.geojson.Geometry;
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

    public <E> GeoJsonResponse wrapAsGeoJson(List<E> entities, Function<E, Double[]> coordinateProvider) {
        GeoJsonResponse geoJsonResponse = new GeoJsonResponse();
        geoJsonResponse.type = "FeatureCollection";
        geoJsonResponse.features = createFeatureCollection(entities, coordinateProvider);

        return geoJsonResponse;
    }

    private <E> List<Feature> createFeatureCollection(List<E> entities, Function<E, Double[]> coordinateProvider) {
        List<Feature> features = new ArrayList<>();
        for (E entity : entities) {
            features.add(createFeature(coordinateProvider, entity));
        }
        return features;
    }

    private <E> Feature createFeature(Function<E, Double[]> coordinateProvider, E entity) {
        Feature feature = new Feature();
        feature.type = "Feature";

        Geometry geometry = new Geometry();
        geometry.type = "Point";
        geometry.coordinates = coordinateProvider.apply(entity);
        feature.geometry = geometry;

        Map propertyMap = objectMapper.convertValue(entity, Map.class);
        propertyMap.remove("location");
        propertyMap.remove("longitude");
        propertyMap.remove("latitude");
        feature.properties = propertyMap;
        return feature;
    }
}
