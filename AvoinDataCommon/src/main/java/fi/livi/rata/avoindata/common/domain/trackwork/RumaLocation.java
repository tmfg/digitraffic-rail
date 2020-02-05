package fi.livi.rata.avoindata.common.domain.trackwork;

import com.vividsolutions.jts.geom.Geometry;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class RumaLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public LocationType locationType;
    public String operatingPointId;
    public String sectionBetweenOperatingPointsId;
    public Geometry locationMap;
    public Geometry locationSchema;

    @ManyToOne(optional = true)
    @JoinColumn(name = "track_work_part_id", referencedColumnName = "id")
    public TrackWorkPart trackWorkPart;

    @OneToMany(mappedBy = "location", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.ALL })
    public Set<IdentifierRange> identifierRanges = new HashSet<>();
}
