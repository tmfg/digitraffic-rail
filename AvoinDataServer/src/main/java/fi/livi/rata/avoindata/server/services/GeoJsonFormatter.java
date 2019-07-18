package fi.livi.rata.avoindata.server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class GeoJsonFormatter {
    @Autowired
    private ObjectMapper objectMapper;

    public <E> Map<String, Object> wrapAsGeoJson(ServerHttpResponse response, List<E> entities, Function<E, Double[]> coordinateProvider) {
        response.getHeaders().add("Content-Type", "application/vnd.geo+json");

        Map<String, Object> output = new HashMap<>();
        output.put("type", "FeatureCollection");
        output.put("features", createFeatureCollection(entities, coordinateProvider));

        return output;
    }

    private <E> List<Map<String, Object>> createFeatureCollection(List<E> entities, Function<E, Double[]> coordinateProvider) {
        List<Map<String, Object>> features = new ArrayList<>();
        for (E entity : entities) {
            Map<String, Object> featureMap = createFeature(coordinateProvider, entity);

            features.add(featureMap);
        }
        return features;
    }

    private <E> Map<String, Object> createFeature(Function<E, Double[]> coordinateProvider, E entity) {
        Map<String, Object> featureMap = new HashMap<>();
        featureMap.put("type", "Feature");

        Map<String, Object> geometryMap = new HashMap<>();
        geometryMap.put("type", "Point");
        geometryMap.put("coordinates", coordinateProvider.apply(entity));
        featureMap.put("geometry", geometryMap);

        Map propertyMap = objectMapper.convertValue(entity, Map.class);
        propertyMap.remove("location");
        propertyMap.remove("longitude");
        propertyMap.remove("latitude");
        featureMap.put("properties", propertyMap);
        return featureMap;
    }
}
