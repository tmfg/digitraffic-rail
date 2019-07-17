package fi.livi.rata.avoindata.server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    public <E> Map<String, Object> wrapAsGeoJson(List<E> entities, Function<E, Double[]> coordinateProvider) {
        Map<String, Object> output = new HashMap<>();
        output.put("type", "FeatureCollection");

        List<Map<String, Object>> features = new ArrayList<>();
        output.put("features", features);

        for (E entity : entities) {
            Map<String, Object> featureMap = createFeature(coordinateProvider, entity);

            features.add(featureMap);
        }

        return output;
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
        featureMap.put("properties", propertyMap);
        return featureMap;
    }
}
