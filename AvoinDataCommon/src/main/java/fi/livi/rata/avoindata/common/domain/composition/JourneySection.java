package fi.livi.rata.avoindata.common.domain.composition;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Entity
@ApiModel(description = "Describes a leg where train's composition is in effect")
public class JourneySection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.PERSIST }, optional = false, orphanRemoval = true)
    @ApiModelProperty("Point in schedule where composition starts")
    public CompositionTimeTableRow beginTimeTableRow;

    @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.PERSIST }, optional = false, orphanRemoval = true)
    @ApiModelProperty("Point in schedule where composition ends")
    public CompositionTimeTableRow endTimeTableRow;

    @OneToMany(mappedBy = "journeysection", fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @OrderBy("location")
    @ApiModelProperty("List of locomotives used on this leg")
    public Set<Locomotive> locomotives = new LinkedHashSet<>();

    @OneToMany(mappedBy = "journeysection", fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @OrderBy("location")
    @ApiModelProperty("List of wagons used on this leg")
    public Set<Wagon> wagons = new LinkedHashSet<>();

    @ManyToOne(optional = false)
    @JoinColumns({
            @JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false),
            @JoinColumn(name = "trainNumber", referencedColumnName = "trainNumber", nullable = false)})
    @JsonIgnore
    public Composition composition;

    public int totalLength;
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
