package fi.livi.rata.avoindata.server.controller.api.geojson;

import java.util.List;

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
