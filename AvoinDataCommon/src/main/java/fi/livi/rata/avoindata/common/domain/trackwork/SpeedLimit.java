package fi.livi.rata.avoindata.common.domain.trackwork;

import jakarta.persistence.Embeddable;

import io.swagger.v3.oas.annotations.media.Schema;

@Embeddable
@Schema(name = "SpeedLimit", title = "SpeedLimit", description = "Speed limit set for a part of a track")
public class SpeedLimit {

    @Schema(description = "Speed limit value", requiredMode = Schema.RequiredMode.REQUIRED)
    public Integer speed;

    @Schema(description = "Speed limit signs used?", requiredMode = Schema.RequiredMode.REQUIRED)
    public boolean signs;

    @Schema(description = "Balises used?", requiredMode = Schema.RequiredMode.REQUIRED)
    public boolean balises;

    public SpeedLimit(final Integer speed, final boolean signs, final boolean balises) {
        this.speed = speed;
        this.signs = signs;
        this.balises = balises;
    }

    public SpeedLimit() {
        // for Hibernate
    }
}
