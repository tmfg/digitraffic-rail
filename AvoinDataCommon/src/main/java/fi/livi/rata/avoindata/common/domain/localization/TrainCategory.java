package fi.livi.rata.avoindata.common.domain.localization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;

import fi.livi.rata.avoindata.common.domain.common.NamedEntity;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "TrainCategory", title = "TrainCategory")
public class TrainCategory implements NamedEntity {

    @Id
    @JsonIgnore
    public Long id;

    @Column
    @Schema(example = "Commuter")
    public String name;

    public TrainCategory() {
    }

    public TrainCategory(final String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof TrainCategory) {
            return ((TrainCategory) obj).id.equals(id);
        }
        else {
            return false;
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
