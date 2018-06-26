package fi.livi.rata.avoindata.common.domain.tracksection;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import io.swagger.annotations.ApiModelProperty;

@Entity
public class TrackSection {
    @Id
    public Long id;
    @ApiModelProperty(example = "JNS")
    public String station;
    @ApiModelProperty(example = "JNS_ERV40")
    public String trackSectionCode;

    @OneToMany(mappedBy = "trackSection", fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    public Set<TrackRange> ranges = new HashSet<>();
}
