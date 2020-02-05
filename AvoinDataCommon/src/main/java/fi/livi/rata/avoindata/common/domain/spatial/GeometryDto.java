package fi.livi.rata.avoindata.common.domain.spatial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public interface GeometryDto<T extends List<?>> {

    @JsonValue
    T getCoordinates();

    @JsonIgnore
    String getType();
}
