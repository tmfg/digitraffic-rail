package fi.livi.rata.avoindata.common.domain.common;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import io.swagger.annotations.ApiModelProperty;

@Embeddable
public class StationEmbeddable {
    @Column
    @ApiModelProperty(example = "HKI")
    public String stationShortCode;

    @Column(name = "station_uic_code")
    @ApiModelProperty(example = "1")
    public int stationUICCode;

    @Column
    @ApiModelProperty(example = "FI")
    public String countryCode;

    public StationEmbeddable() {

    }

    public StationEmbeddable(final String stationShortCode, final int stationUICCode, final String countryCode) {
        this.stationShortCode = stationShortCode;
        this.stationUICCode = stationUICCode;
        this.countryCode = countryCode;
    }

    @SuppressWarnings("ControlFlowStatementWithoutBraces")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StationEmbeddable)) return false;

        StationEmbeddable station = (StationEmbeddable) o;

        if (stationUICCode != station.stationUICCode) return false;
        if (!countryCode.equals(station.countryCode)) return false;
        if (!stationShortCode.equals(station.stationShortCode)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = stationShortCode.hashCode();
        result = 31 * result + stationUICCode;
        result = 31 * result + countryCode.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "StationEmbeddable{" +
                "stationShortCode='" + stationShortCode + '\'' +
                '}';
    }
}
