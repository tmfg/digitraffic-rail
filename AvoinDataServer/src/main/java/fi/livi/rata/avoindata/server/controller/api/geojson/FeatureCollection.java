package fi.livi.rata.avoindata.server.controller.api.geojson;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModelProperty;

@JsonPropertyOrder({"type", "features"})
public final class FeatureCollection implements GeoJsonObject {

    @ApiModelProperty(dataType = "fi.livi.rata.avoindata.server.dto.SwaggerObject")
    public final List<Feature> features;

    public FeatureCollection(final List<Feature> features) {
        this.features = features;
    }

    @Override
    public String getType() {
        return "FeatureCollection";
    }
}
