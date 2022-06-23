package fi.livi.rata.avoindata.common.domain.trackwork;

import javax.persistence.Embeddable;

import io.swagger.v3.oas.annotations.media.Schema;

@Embeddable
@Schema(name = "SpeedLimit", description = "Speed limit set for a part of a track")
public class SpeedLimit {

    @Schema(description = "Speed limit value", required = true)
    public Integer speed;

    @Schema(description = "Speed limit signs used?", required = true)
    public boolean signs;

    @Schema(description = "Balises used?", required = true)
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
