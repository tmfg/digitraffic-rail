package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Returns properties: \n" +
        "\"type\": corresponds with GeoJSON geometry types\n" +
        "\"coordinates\": (nested) list(s) of floating point numbers ")
public interface GeometryDto<T extends List<?>> {

    @JsonValue
    T getCoordinates();

    @JsonIgnore
    String getType();
}
