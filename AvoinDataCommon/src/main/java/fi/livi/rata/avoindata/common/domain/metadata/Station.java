package fi.livi.rata.avoindata.common.domain.metadata;

import java.math.BigDecimal;

import javax.persistence.Entity;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
public class Station {
    @JsonProperty("stationName")
    @Schema(example = "Helsinki asema")
    public String name;
    @JsonProperty("stationShortCode")
    @Schema(example = "HKI")
    public String shortCode;
    @JsonProperty("stationUICCode")
    @Schema(example = "1")
    public int uicCode;
    @JsonProperty(value = "countryCode", required = false)
    @Schema(example = "FI")
    public String countryCode;
    @JsonProperty("longitude")
    @Schema(example = "24.94166179681815")
    public BigDecimal longitude;
    @JsonProperty("latitude")
    @Schema(example = "60.17212991202909")
    public BigDecimal latitude;

    @Id
    @JsonIgnore
    public Long id;

    @Schema(description = "Does this station have passenger traffic", example = "true")
    public Boolean passengerTraffic;
    @Schema(description = "Type of station", example = "STATION,STOPPING_POINT")
    public StationTypeEnum type;
}
