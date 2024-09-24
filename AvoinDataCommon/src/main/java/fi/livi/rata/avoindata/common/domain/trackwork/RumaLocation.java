package fi.livi.rata.avoindata.common.domain.trackwork;

import java.util.HashSet;
import java.util.Set;

import org.locationtech.jts.geom.Geometry;

import fi.livi.rata.avoindata.common.converter.RumaLocationTypeConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

@Entity
public class RumaLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    // the db column stores ordinals and is of type varchar. As of Hibernate 6.2,
    // varchar ordinals cannot be mapped to Java enums.
    // This is a temporary fix. The db table is several gbs in size
    // so a schema modification will take some time
    @Convert(converter = RumaLocationTypeConverter.class)
    public LocationType locationType;
    public String operatingPointId;
    public String sectionBetweenOperatingPointsId;
    public Geometry locationMap;
    public Geometry locationSchema;

    @OneToMany(mappedBy = "location",
               fetch = FetchType.EAGER,
               cascade = { CascadeType.REMOVE, CascadeType.ALL })
    public Set<IdentifierRange> identifierRanges = new HashSet<>();

}
