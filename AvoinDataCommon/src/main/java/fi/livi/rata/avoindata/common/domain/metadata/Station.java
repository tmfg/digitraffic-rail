package fi.livi.rata.avoindata.common.domain.metadata;

import java.math.BigDecimal;
import javax.persistence.Entity;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;

@Entity
public class Station {
    @JsonProperty("stationName")
    @ApiModelProperty(example = "Helsinki asema")
    public String name;
    @JsonProperty("stationShortCode")
    @ApiModelProperty(example = "HKI")
    public String shortCode;
    @JsonProperty("stationUICCode")
    @ApiModelProperty(example = "1")
    public int uicCode;
    @JsonProperty(value = "countryCode", required = false)
    @ApiModelProperty(example = "FI")
    public String countryCode;
    @JsonProperty("longitude")
    @ApiModelProperty(example = "24.94166179681815")
    public BigDecimal longitude;
    @JsonProperty("latitude")
    @ApiModelProperty(example = "60.17212991202909")
    public BigDecimal latitude;

    @Id
    @JsonIgnore
    public Long id;

    @ApiModelProperty(value = "Does this station have passenger traffic",example = "true")
    public Boolean passengerTraffic;
    @ApiModelProperty(value = "Type of station", example = "STATION,STOPPING_POINT")
    public StationTypeEnum type;
}
