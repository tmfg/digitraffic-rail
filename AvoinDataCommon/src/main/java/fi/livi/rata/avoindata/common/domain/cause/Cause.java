package fi.livi.rata.avoindata.common.domain.cause;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.annotations.ApiModel;

@Entity
@ApiModel(description="Details why a train is not on schedule. Train-responses only have ids and codes populated.")
public class Cause {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @JsonIgnore
    public Long id;

    @ManyToOne
    @JoinColumns({@JoinColumn(name = "departureDate", referencedColumnName = "departureDate", nullable = false), @JoinColumn(name =
            "trainNumber", referencedColumnName = "trainNumber", nullable = false), @JoinColumn(name = "attapId", referencedColumnName =
            "attapId", nullable = false)})
    @JsonIgnore
    public TimeTableRow timeTableRow;

    @JsonIgnore
    @Transient
    public long version;

    @ManyToOne
    @JsonUnwrapped
    public CategoryCode categoryCode;

    @ManyToOne
    @JsonUnwrapped
    public DetailedCategoryCode detailedCategoryCode;

    @ManyToOne
    @JsonUnwrapped
    public ThirdCategoryCode thirdCategoryCode;
}
