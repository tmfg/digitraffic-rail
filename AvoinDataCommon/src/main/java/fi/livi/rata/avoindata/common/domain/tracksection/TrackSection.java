package fi.livi.rata.avoindata.common.domain.tracksection;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "TrackSection", title = "TrackSection")
public class TrackSection {
    @Id
    public Long id;
    @Schema(example = "JNS")
    public String station;
    @Schema(example = "JNS_ERV40")
    public String trackSectionCode;

    @OneToMany(mappedBy = "trackSection", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    public Set<TrackRange> ranges = new HashSet<>();
}
