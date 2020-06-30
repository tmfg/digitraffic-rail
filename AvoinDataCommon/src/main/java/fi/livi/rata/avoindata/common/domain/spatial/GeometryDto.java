package fi.livi.rata.avoindata.common.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

@ApiModel(description = "Returns properties: \n" +
        "\"type\": corresponds with GeoJSON geometry types\n" +
        "\"coordinates\": (nested) list(s) of floating point numbers ")
public interface GeometryDto<T extends List<?>> {

    @JsonValue
    T getCoordinates();

    @JsonIgnore
    String getType();
}
