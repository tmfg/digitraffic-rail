package fi.livi.rata.avoindata.common.domain.trackwork;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;

@Embeddable
@ApiModel(description = "Speed limit set for a part of a track")
public class SpeedLimit {

    @ApiModelProperty(value = "Speed limit value", required = true)
    public Integer speed;

    @ApiModelProperty(value = "Speed limit signs used?", required = true)
    public boolean signs;

    @ApiModelProperty(value = "Balises used?", required = true)
    public boolean balises;

    public SpeedLimit(Integer speed, boolean signs, boolean balises) {
        this.speed = speed;
        this.signs = signs;
        this.balises = balises;
    }

    public SpeedLimit() {
        // for Hibernate
    }
}
