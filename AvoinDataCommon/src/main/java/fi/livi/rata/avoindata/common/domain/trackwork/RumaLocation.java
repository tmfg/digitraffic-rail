package fi.livi.rata.avoindata.common.domain.trackwork;


import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

import org.locationtech.jts.geom.Geometry;

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
