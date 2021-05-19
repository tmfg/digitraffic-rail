package fi.livi.rata.avoindata.common.domain.cause;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Entity
@ApiModel(description="A code that is used to categorize reasons for a train not being on schedule")
public class CategoryCode extends ACauseCode {
    @Id
    @JsonView(CategoryCodeJsonView.All.class)
    public String oid;

    @JsonView({CategoryCodeJsonView.OnlyCauseCategoryCodes.class, CategoryCodeJsonView.All.class})
    @ApiModelProperty(value = "Official code",example = "E",required = true)
    public String categoryCode;

    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(value = "Official name",example = "Etuajassakulku",required = true)
    public String categoryName;

    @Column
    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(value = "Start date when this code is used",required = true)
    public LocalDate validFrom;

    @Column
    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(value = "End date when this code is used. Empty means category is used until further notice")
    public LocalDate validTo;

    @OneToMany(mappedBy = "categoryCode", fetch = FetchType.LAZY)
    @OrderBy
    @JsonIgnore
    public Set<DetailedCategoryCode> detailedCategoryCodes = new HashSet<>();

    @Override
    public String getIdString() {
        return this.oid;
    }
}
