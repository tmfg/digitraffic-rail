package fi.livi.rata.avoindata.common.domain.trackwork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vividsolutions.jts.geom.Geometry;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;

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
    @JsonIgnore
    public TrackWorkPart trackWorkPart;

    @JoinColumns({
            @JoinColumn(name = "trn_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "trn_version", referencedColumnName = "version", nullable = false)
    })
    @ManyToOne(optional = false)
    @JsonIgnore
    public TrafficRestrictionNotification trafficRestrictionNotification;

    @OneToMany(mappedBy = "location", fetch = FetchType.EAGER, cascade = { CascadeType.REMOVE, CascadeType.ALL })
    public Set<IdentifierRange> identifierRanges = new HashSet<>();
}
