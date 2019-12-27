package fi.livi.rata.avoindata.common.domain.trackwork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.common.converter.StringListConverter;

import javax.persistence.*;
import java.util.List;

@Entity
public class ElementRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String elementId1;
    public String elementId2;
    private String trackKilometerRange;

    @JoinColumn(name = "identifier_range_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    @JsonIgnore
    public IdentifierRange identifierRange;

    @Convert(converter = StringListConverter.class)
    public List<String> trackIds;

    @Convert(converter = StringListConverter.class)
    public List<String> specifiers;

}
