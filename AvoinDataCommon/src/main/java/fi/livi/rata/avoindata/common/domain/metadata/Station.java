package fi.livi.rata.avoindata.common.domain.metadata;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.SerializableSerializer;
import fi.livi.rata.avoindata.common.serializer.BigDecimalSerializer;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "Station", title = "Station")
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
    @JsonSerialize(using = BigDecimalSerializer.class)
    @Schema(example = "24.941662")
    public BigDecimal longitude;

    @JsonProperty("latitude")
    @JsonSerialize(using = BigDecimalSerializer.class)
    @Schema(example = "60.172133")
    public BigDecimal latitude;

    @Id
    @JsonIgnore
    public Long id;

    @Schema(description = "Does this station have passenger traffic", example = "true")
    public Boolean passengerTraffic;
    @Schema(description = "Type of station", example = "STATION,STOPPING_POINT")
    public StationTypeEnum type;
}
