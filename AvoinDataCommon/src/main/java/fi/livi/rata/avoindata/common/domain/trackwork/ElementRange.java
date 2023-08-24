package fi.livi.rata.avoindata.common.domain.trackwork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.common.converter.StringListConverter;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class ElementRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    public String elementId1;
    public String elementId2;
    public String trackKilometerRange;

    @JoinColumn(name = "identifier_range_id", referencedColumnName = "id")
    @ManyToOne(optional = false)
    @JsonIgnore
    public IdentifierRange identifierRange;

    @Convert(converter = StringListConverter.class)
    public List<String> trackIds;

    @Convert(converter = StringListConverter.class)
    public List<String> specifiers;

    @Override
    public String toString() {
        return "ElementRange{" +
                "elementId1='" + elementId1 + '\'' +
                ", elementId2='" + elementId2 + '\'' +
                '}';
    }
}
