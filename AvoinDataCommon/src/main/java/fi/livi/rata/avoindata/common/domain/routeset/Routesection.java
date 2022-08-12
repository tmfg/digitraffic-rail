package fi.livi.rata.avoindata.common.domain.routeset;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "Routesection", title = "Routesection")
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
