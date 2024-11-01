package fi.livi.rata.avoindata.common.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredMode = Schema.RequiredMode.REQUIRED)
@Embeddable
public class StationEmbeddable {
    @Column
    @Schema(example = "HKI")
    public String stationShortCode;

    @Column(name = "station_uic_code")
    @Schema(example = "1")
    public int stationUICCode;

    @Column
    @Schema(example = "FI")
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final StationEmbeddable station)) return false;

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
