package fi.livi.rata.avoindata.common.domain.routeset;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class Routesection {
    @Id
    @JsonIgnore
    public Long id;

    @ManyToOne
    @JsonIgnore
    public Routeset routeset;

    public String sectionId;
    public String stationCode;
    public String commercialTrackId;

    @JsonIgnore
    public int sectionOrder;
}
