package fi.livi.rata.avoindata.common.domain.trainlocation;

import java.util.List;

//Made because Swagger documentation does not like jts Point
public class SwaggerPoint {
    public String type;
    public List<Double> coordinates;
}
