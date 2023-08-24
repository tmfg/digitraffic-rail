package fi.livi.rata.avoindata.common.domain.trainlocation;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "TrainLocation", title = "TrainLocation")
public class TrainLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Embedded
    @JsonUnwrapped
    public TrainLocationId trainLocationId;

    @Schema(type = "fi.livi.rata.avoindata.common.domain.trainlocation.SwaggerPoint", implementation = SwaggerPoint.class, name = "SwaggerPoint", title = "SwaggerPoint")
    public Point location;

    public Integer speed;

    public Integer accuracy;

    @Transient
    @JsonIgnore
    public Point liikeLocation;

    @Override
    public String toString() {
        return "TrainLocation{" + "trainLocationId=" + trainLocationId + '}';
    }
}
