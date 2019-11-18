package fi.livi.rata.avoindata.common.domain.trackwork;

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
    private String ratakmvali;

    @ManyToOne(optional = false)
    public IdentifierRange identifierRange;

    @Convert(converter = StringListConverter.class)
    private List<String> raideIds;

    @Convert(converter = StringListConverter.class)
    private List<String> specifiers;

}
