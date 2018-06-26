package fi.livi.rata.avoindata.common.domain.cause;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Entity
@ApiModel(description = "Category code that is one step more detailed from its parent CategoryCode")
public class DetailedCategoryCode extends ACauseCode {
    @Id
    @JsonView(CategoryCodeJsonView.All.class)
    public Long id;

    @JsonView({CategoryCodeJsonView.OnlyCauseCategoryCodes.class, CategoryCodeJsonView.All.class})
    @ApiModelProperty(example = "E2")
    public String detailedCategoryCode;

    @ColumnDefault("")
    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(example="Ajo- tai pysähdysajan alitus")
    public String detailedCategoryName;

    @Column
    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(value = "Start date when this category code is used",required = true)
    public LocalDate validFrom;

    @Column
    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(value = "End date when this code is used. Empty means category is used until further notice")
    public LocalDate validTo;

    @ManyToOne
    @JoinColumn(name = "category_code_id", referencedColumnName = "id", nullable = false)
    @JsonIgnore
    public CategoryCode categoryCode;

    @OneToMany(mappedBy = "detailedCategoryCode", fetch = FetchType.LAZY)
    @OrderBy
    @JsonIgnore
    public Set<ThirdCategoryCode> thirdCategoryCodes = new HashSet<>();

    @Override
    public String getIdString() {
        return String.format("%s_%s",detailedCategoryCode,id);
    }
}
