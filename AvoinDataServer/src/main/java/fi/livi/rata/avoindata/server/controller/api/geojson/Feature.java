package fi.livi.rata.avoindata.server.controller.api.geojson;

import java.util.Map;

public class Feature extends AGeoJson {
    public Geometry geometry;

    public Map<String, Object> properties;
}
