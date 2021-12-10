package fi.livi.rata.avoindata.server.controller.api.geojson;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"type", "geometry", "properties"})
public class Feature implements GeoJsonObject {

    public com.vividsolutions.jts.geom.Geometry geometry;
    public Object properties;

    public Feature(com.vividsolutions.jts.geom.Geometry geometry, Object properties) {
        this.geometry = geometry;
        this.properties = properties;
    }

    @Override
    public String getType() {
        return "Feature";
    }
}
