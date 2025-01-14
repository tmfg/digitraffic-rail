package fi.livi.rata.avoindata.server.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;
import fi.livi.rata.avoindata.server.controller.api.geojson.FeatureCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static fi.livi.rata.avoindata.common.serializer.BigDecimalSerializer.scale;

@Service
public class GeoJsonFormatter {
    @Autowired
    private ObjectMapper objectMapper;

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    public <E> FeatureCollection wrapAsGeoJson(final List<E> entities, final Function<E, Double[]> coordinateProvider) {
        return new FeatureCollection(createFeatureCollection(entities, coordinateProvider));
    }

    private <E> List<Feature> createFeatureCollection(final List<E> entities, final Function<E, Double[]> coordinateProvider) {
        final List<Feature> features = new ArrayList<>();
        for (final E entity : entities) {
            features.add(createFeature(coordinateProvider, entity));
        }
        return features;
    }

    private <E> Feature createFeature(final Function<E, Double[]> coordinateProvider, final E entity) {
        final var propertyMap = objectMapper.convertValue(entity, Map.class);
        propertyMap.remove("location");
        propertyMap.remove("longitude");
        propertyMap.remove("latitude");

        final Double[] coords = coordinateProvider.apply(entity);

        return new Feature(geometryFactory.createPoint(new Coordinate(coords[0], coords[1])), propertyMap);
    }
}
