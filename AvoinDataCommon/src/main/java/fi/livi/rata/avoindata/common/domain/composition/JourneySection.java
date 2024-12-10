package fi.livi.rata.avoindata.common.domain.composition;

import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "JourneySection", title = "JourneySection", description = "Describes a leg where train's composition is in effect")
public class JourneySection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.PERSIST }, optional = false, orphanRemoval = true)
    @Schema(description = "Point in schedule where composition starts")
    public CompositionTimeTableRow beginTimeTableRow;

    @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.PERSIST }, optional = false, orphanRemoval = true)
    @Schema(description = "Point in schedule where composition ends")
    public CompositionTimeTableRow endTimeTableRow;

    @OneToMany(mappedBy = "journeysection", fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @OrderBy("location")
    @Schema(description = "List of locomotives used on this leg")
    public Set<Locomotive> locomotives = new LinkedHashSet<>();

    @OneToMany(mappedBy = "journeysection", fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @OrderBy("location")
    @Schema(description = "List of wagons used on this leg")
    public Set<Wagon> wagons = new LinkedHashSet<>();

    @ManyToOne(optional = false)
    @JoinColumns({
            @JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false),
            @JoinColumn(name = "trainNumber", referencedColumnName = "trainNumber", nullable = false)})
    @JsonIgnore
    public Composition composition;

    @Schema(description = "Total length of the train with an accuracy of 1 m")
    public int totalLength;

    @Schema(description = "Maximum with an accuracy of 1 km/h")
    public int maximumSpeed;

    public Long attapId;
    public Long saapAttapId;

    protected JourneySection() {
    }

    public JourneySection(final CompositionTimeTableRow beginTimeTableRow, final CompositionTimeTableRow endTimeTableRow,
                          final Composition composition, final int maximumSpeed, final int totalLength, final Long attapId, final Long saapAttapId) {
        this.beginTimeTableRow = beginTimeTableRow;
        this.endTimeTableRow = endTimeTableRow;
        this.composition = composition;
        this.maximumSpeed = maximumSpeed;
        this.totalLength = totalLength;
        this.attapId = attapId;
        this.saapAttapId = saapAttapId;
    }
}
