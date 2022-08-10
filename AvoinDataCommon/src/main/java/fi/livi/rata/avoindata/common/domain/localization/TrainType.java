package fi.livi.rata.avoindata.common.domain.localization;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import fi.livi.rata.avoindata.common.domain.common.NamedEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "TrainType", title = "TrainType")
public class TrainType implements NamedEntity {

    @Id
    @JsonIgnore
    public Long id;

    @JsonIgnore
    @Transient
    public boolean commercial;

    @Column
    @Schema(example = "HL")
    public String name;

    @OneToOne(cascade = CascadeType.REMOVE, optional = false, orphanRemoval = false)
    public TrainCategory trainCategory;

    public TrainType() {
    }

    public TrainType(final String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }
}
