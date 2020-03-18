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

    @OneToMany(mappedBy = "location", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.ALL })
    public Set<IdentifierRange> identifierRanges = new HashSet<>();


}
