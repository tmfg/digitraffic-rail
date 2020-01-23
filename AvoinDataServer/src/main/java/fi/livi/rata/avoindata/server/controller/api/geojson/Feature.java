package fi.livi.rata.avoindata.server.controller.api.geojson;

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
