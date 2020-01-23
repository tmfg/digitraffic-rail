package fi.livi.rata.avoindata.server.controller.api.geojson;

import java.util.List;

public interface Geometry<T extends List<?>> extends GeoJsonObject {

    T getCoordinates();

}
