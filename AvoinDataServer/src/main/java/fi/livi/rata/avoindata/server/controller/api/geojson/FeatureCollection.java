package fi.livi.rata.avoindata.server.controller.api.geojson;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"type", "features"})
public final class FeatureCollection implements GeoJsonObject {

    public final List<Feature> features;

    public FeatureCollection(final List<Feature> features) {
        this.features = features;
    }

    @Override
    public String getType() {
        return "FeatureCollection";
    }
}
