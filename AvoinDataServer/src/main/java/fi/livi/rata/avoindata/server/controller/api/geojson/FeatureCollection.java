package fi.livi.rata.avoindata.server.controller.api.geojson;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonPropertyOrder({"type", "features"})
@Schema(name = "FeatureCollection", title = "FeatureCollection")
public final class FeatureCollection implements GeoJsonObject {

    @Schema(type = "fi.livi.rata.avoindata.server.dto.SwaggerObject")
    public final List<Feature> features;

    public FeatureCollection(final List<Feature> features) {
        this.features = features;
    }

    @Override
    public String getType() {
        return "FeatureCollection";
    }
}
