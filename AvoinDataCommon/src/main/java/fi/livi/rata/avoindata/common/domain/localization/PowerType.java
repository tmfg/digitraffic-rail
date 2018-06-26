package fi.livi.rata.avoindata.common.domain.localization;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class PowerType {

    @Id
    public Long id;

    @Column
    @JsonProperty("nimi")
    public String name;

    @Column
    @JsonProperty("lyhenne")
    public String abbreviation;

    public PowerType() {
    }

    public PowerType(final String name) {
        this.name = name;
    }
}
