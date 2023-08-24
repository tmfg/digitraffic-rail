package fi.livi.rata.avoindata.common.domain.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "Locomotive", title = "Locomotive")
public class Locomotive {

    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Column
    @Schema(description = "Location of the locomotive in the train. 1=start of the train", example = "1")
    public int location;

    @Column
    @Schema(example = "Sm3")
    public String locomotiveType;

    @Column
    @JsonIgnore
    public String powerTypeAbbreviation;

    @Transient
    @Schema(example = "S")
    public String powerType;

    @Schema(example = "94102081010-2")
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
