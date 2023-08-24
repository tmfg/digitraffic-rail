package fi.livi.rata.avoindata.common.domain.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "Wagon", title = "Wagon")
public class Wagon {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @Schema(example = "Ed")
    public String wagonType;

    @Column
    @Schema(example = "1")
    public int location;
    @Column
    @Schema(description = "Wagon number in customer's ticket", example = "1")
    public int salesNumber;
    @Column
    @Schema(description = "Wagon length in centimeters", example = "2640")
    public int length;
    @Column
    public Boolean playground;
    @Column
    public Boolean pet;
    @Column
    public Boolean catering;
    @Column
    public Boolean video;
    @Column
    public Boolean luggage;
    @Column
    public Boolean smoking;
    @Column
    public Boolean disabled;
    @Schema(example = "94102081010-2")
    @Column
    public String vehicleNumber;
    @ManyToOne
    @JoinColumn(name = "journeysection", nullable = false)
    @JsonIgnore
    public JourneySection journeysection;

    public Wagon(final Wagon wagon, final JourneySection journeysection) {
        location = wagon.location;
        salesNumber = wagon.salesNumber;
        length = wagon.length;
        playground = wagon.playground;
        pet = wagon.pet;
        catering = wagon.catering;
        video = wagon.video;
        luggage = wagon.luggage;
        smoking = wagon.smoking;
        disabled = wagon.disabled;
        this.journeysection = journeysection;
    }

    public Wagon() {
    }
}
