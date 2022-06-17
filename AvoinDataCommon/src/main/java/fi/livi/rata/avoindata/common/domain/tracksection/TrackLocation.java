package fi.livi.rata.avoindata.common.domain.tracksection;

import javax.persistence.Embeddable;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Embeddable
@Schema(description = "A location on the track. Kilometres and meters are distance from the start of the track")
public class TrackLocation {
    @ApiModelProperty(example = "003")
    public String track;
    @ApiModelProperty(example = "34")
    public Integer kilometres;
    @ApiModelProperty(example = "940")
    public Integer metres;
}
