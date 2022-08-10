package fi.livi.rata.avoindata.server.controller.api.geojson;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(subTypes = {FeatureCollection.class, Feature.class})
public interface GeoJsonObject {

    String getType();

}
