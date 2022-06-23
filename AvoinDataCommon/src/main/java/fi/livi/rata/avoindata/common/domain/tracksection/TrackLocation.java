package fi.livi.rata.avoindata.common.domain.tracksection;

import javax.persistence.Embeddable;

import io.swagger.v3.oas.annotations.media.Schema;

@Embeddable
@Schema(name = "TrackLocation", description = "A location on the track. Kilometres and meters are distance from the start of the track")
public class TrackLocation {
    @Schema(example = "003")
    public String track;
    @Schema(example = "34")
    public Integer kilometres;
    @Schema(example = "940")
    public Integer metres;
}
