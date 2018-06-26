package fi.livi.rata.avoindata.common.domain.localization;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;

@Entity
public class TrainCategory {

    @Id
    @JsonIgnore
    public Long id;

    @Column
    @ApiModelProperty(example = "Commuter")
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
}
