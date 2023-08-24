package fi.livi.rata.avoindata.common.domain.tracksection;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "TrackRange", title = "TrackRange")
public class TrackRange {
    @Id
    public Long id;

    @AttributeOverrides({
            @AttributeOverride(name="track",column=@Column(name="start_track")),
            @AttributeOverride(name="kilometres",column=@Column(name="start_kilometres")),
            @AttributeOverride(name="metres",column=@Column(name="start_metres")),
            })
    @Embedded
    public TrackLocation startLocation;

    @AttributeOverrides({
            @AttributeOverride(name="track",column=@Column(name="end_track")),
            @AttributeOverride(name="kilometres",column=@Column(name="end_kilometres")),
            @AttributeOverride(name="metres",column=@Column(name="end_metres")),
    })
    @Embedded
    public TrackLocation endLocation;

    @ManyToOne
    @JsonIgnore
    public TrackSection trackSection;
}
