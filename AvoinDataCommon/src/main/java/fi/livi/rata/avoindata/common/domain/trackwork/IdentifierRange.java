package fi.livi.rata.avoindata.common.domain.trackwork;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

import org.locationtech.jts.geom.Geometry;

@Entity
public class IdentifierRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String elementId;
    public String elementPairId1;
    public String elementPairId2;
    public Geometry locationMap;
    public Geometry locationSchema;

    @Embedded
    public SpeedLimit speedLimit;

    @JoinColumn(name = "ruma_location_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    @JsonIgnore
    public RumaLocation location;

    @OneToMany(mappedBy = "identifierRange", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.ALL })
    public Set<ElementRange> elementRanges = new HashSet<>();

    @Override
    public String toString() {
        return "IdentifierRange{" +
                "elementId='" + elementId + '\'' +
                ", elementRanges=" + elementRanges +
                '}';
    }
}
