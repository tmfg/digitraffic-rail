package fi.livi.rata.avoindata.common.domain.localization;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonProperty;
import fi.livi.rata.avoindata.common.domain.common.NamedEntity;

@Entity
public class PowerType implements NamedEntity {

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

    @Override
    public String getName() {
        return name;
    }
}
