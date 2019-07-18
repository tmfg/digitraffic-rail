package fi.livi.rata.avoindata.server.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;

import javax.persistence.Embedded;

public class TrainLocationV2 {
    @Embedded
    @JsonUnwrapped
    public final TrainLocationId trainLocationId;

    public final Double[] location;

    public final Integer speed;

    public TrainLocationV2(TrainLocation trainLocation) {
        this.trainLocationId = trainLocation.trainLocationId;
        this.speed = trainLocation.speed;
        this.location = new Double[]{trainLocation.location.getX(), trainLocation.location.getY()};

    }

    @Override
    public String toString() {
        return "TrainLocationV2{" + "trainLocationId=" + trainLocationId + '}';
    }
}
