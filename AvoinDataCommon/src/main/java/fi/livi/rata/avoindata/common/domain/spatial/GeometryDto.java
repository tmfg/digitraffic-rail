package fi.livi.rata.avoindata.common.domain.spatial;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
        Returns properties:\s
        "type": corresponds with GeoJSON geometry types
        "coordinates": (nested) list(s) of floating point numbers\s""")
public interface GeometryDto<T extends List<?>> {

    @JsonValue
    T getCoordinates();

    @JsonIgnore
    String getType();
}
