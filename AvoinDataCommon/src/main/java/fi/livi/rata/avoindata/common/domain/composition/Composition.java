package fi.livi.rata.avoindata.common.domain.composition;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table
@Schema(name = "Composition", title = "Composition", description = "Describes train's locomotives and wagons")
public class Composition  {

    @EmbeddedId
    @JsonUnwrapped
    public TrainId id;

    @Embedded
    @JsonUnwrapped
    @JsonInclude(Include.NON_NULL) // Springdoc won't include embedded to OpenAPI schemas without JsonInclude
    public Operator operator;

    @Transient
    @Schema(example = "Long-distance", required = true)
    public String trainCategory;
    @Transient
    @Schema(example = "IC", required = true)
    public String trainType;
    @Column
    @JsonIgnore
    public long trainCategoryId;
    @Column
    @JsonIgnore
    public long trainTypeId;

    @Column
    @Schema(description = "When was this data last modified", example = "253328854733")
    public Long version;

    @OneToMany(mappedBy = "composition", fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @OrderBy
    public Set<JourneySection> journeySections = new LinkedHashSet<>();

    protected Composition() {
    }

    public Composition(final Operator operator, final Long trainNumber, final LocalDate departureDate, final long trainCategoryId,
            final long trainTypeId, final long version) {
        this.id=new TrainId(trainNumber, departureDate);
        this.operator = operator;
        this.trainCategoryId = trainCategoryId;
        this.trainTypeId = trainTypeId;
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Composition)) return false;

        Composition that = (Composition) o;

        if (!id.equals(that.id)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
