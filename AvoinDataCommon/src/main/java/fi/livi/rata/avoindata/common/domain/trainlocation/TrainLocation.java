package fi.livi.rata.avoindata.common.domain.trainlocation;

import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
public class TrainLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Embedded
    @JsonUnwrapped
    public TrainLocationId trainLocationId;

    @Schema(type = "fi.livi.rata.avoindata.common.domain.trainlocation.SwaggerPoint")
    public Point location;

    public Integer speed;

    @Transient
    @JsonIgnore
    public Point liikeLocation;

    @Override
    public String toString() {
        return "TrainLocation{" + "trainLocationId=" + trainLocationId + '}';
    }
}
