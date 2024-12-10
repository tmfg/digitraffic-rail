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
        this.location = wagon.location;
        this.salesNumber = wagon.salesNumber;
        this.length = wagon.length;
        this.playground = wagon.playground;
        this.pet = wagon.pet;
        this.catering = wagon.catering;
        this.video = wagon.video;
        this.luggage = wagon.luggage;
        this.smoking = wagon.smoking;
        this.disabled = wagon.disabled;
        this.journeysection = journeysection;
    }

    public Wagon() {
    }

    public Wagon(final String wagonType, final int location, final int salesNumber, final int length, final Boolean playground,
                 final Boolean pet, final Boolean catering, final Boolean video,
                 final Boolean luggage, final Boolean smoking, final Boolean disabled, final String vehicleNumber) {
        this.wagonType = wagonType;
        this.location = location;
        this.salesNumber = salesNumber;
        this.length = length;
        this.playground = playground;
        this.pet = pet;
        this.catering = catering;
        this.video = video;
        this.luggage = luggage;
        this.smoking = smoking;
        this.disabled = disabled;
        this.vehicleNumber = vehicleNumber;
    }

    @Override
    public String toString() {
        return "Wagon{" +
                "id=" + id +
                ", wagonType='" + wagonType + '\'' +
                ", location=" + location +
                ", salesNumber=" + salesNumber +
                ", length=" + length +
                ", playground=" + playground +
                ", pet=" + pet +
                ", catering=" + catering +
                ", video=" + video +
                ", luggage=" + luggage +
                ", smoking=" + smoking +
                ", disabled=" + disabled +
                ", vehicleNumber='" + vehicleNumber + '\'' +
                ", journeysection=" + journeysection +
                '}';
    }
}
