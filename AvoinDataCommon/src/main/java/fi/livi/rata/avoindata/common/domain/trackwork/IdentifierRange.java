package fi.livi.rata.avoindata.common.domain.trackwork;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class IdentifierRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String elementId;
    public String elementPairId1;
    public String elementPairId2;

    @Embedded
    public SpeedLimit speedLimit;

    @JoinColumn(name = "ruma_location_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    public RumaLocation location;

    @OneToMany(mappedBy = "identifierRange", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.PERSIST })
    public Set<ElementRange> elementRanges = new HashSet<>();

}
