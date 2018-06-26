package fi.livi.rata.avoindata.common.domain.trainlocation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.vividsolutions.jts.geom.Point;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;


@Entity
public class TrainLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Embedded
    @JsonUnwrapped
    public TrainLocationId trainLocationId;

    @ApiModelProperty(dataType = "fi.livi.rata.avoindata.common.domain.trainlocation.SwaggerPoint")
    public Point location;

    public Integer speed;

    @JsonIgnore
    public TrainLocationConnectionQuality connectionQuality;

    @Transient
    @JsonIgnore
    public Point liikeLocation;

    @Override
    public String toString() {
        return "TrainLocation{" + "trainLocationId=" + trainLocationId + '}';
    }
}
