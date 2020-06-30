package fi.livi.rata.avoindata.common.domain.composition;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Locomotive {

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Column
    @ApiModelProperty(value = "Location of the locomotive in the train. 1=start of the train", example = "1")
    public int location;

    @Column
    @ApiModelProperty(example = "Sm3")
    public String locomotiveType;

    @Column
    @JsonIgnore
    public String powerTypeAbbreviation;

    @Transient
    @ApiModelProperty(example = "S")
    public String powerType;

    @ApiModelProperty(example = "94102081010-2")
    @Column
    public String vehicleNumber;

    @ManyToOne
    @JoinColumn(name = "journeysection", nullable = false)
    @JsonIgnore
    public JourneySection journeysection;

    public Locomotive() {
    }

    public Locomotive(final Locomotive locomotive, final JourneySection journeysection) {
        location = locomotive.location;
        locomotiveType = locomotive.locomotiveType;
        powerTypeAbbreviation = locomotive.powerTypeAbbreviation;
        vehicleNumber = locomotive.vehicleNumber;
        this.journeysection = journeysection;
    }
}
