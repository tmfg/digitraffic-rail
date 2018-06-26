package fi.livi.rata.avoindata.common.domain.cause;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import io.swagger.annotations.ApiModel;

import javax.persistence.*;

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

    @Transient
    @JsonView(CategoryCodeJsonView.OnlyCauseCategoryCodes.class)
    public Long getCategoryCodeId() {
        if (categoryCode != null) {
            return categoryCode.id;
        }
        return null;
    }

    @ManyToOne
    @JsonUnwrapped
    public DetailedCategoryCode detailedCategoryCode;

    @Transient
    @JsonView(CategoryCodeJsonView.OnlyCauseCategoryCodes.class)
    public Long getDetailedCategoryCodeId() {
        if (detailedCategoryCode != null) {
            return detailedCategoryCode.id;
        }
        return null;
    }

    @ManyToOne
    @JsonUnwrapped
    public ThirdCategoryCode thirdCategoryCode;

    @Transient
    @JsonView(CategoryCodeJsonView.OnlyCauseCategoryCodes.class)
    public Long getThirdCategoryCodeId() {
        if (thirdCategoryCode != null) {
            return thirdCategoryCode.id;
        }
        return null;
    }
}
