package fi.livi.rata.avoindata.common.domain.composition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class Locomotive {

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Column
    @ApiModelProperty("Location of the locomotive in the train. 1=start of the train")
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
        this.journeysection = journeysection;
    }
}
