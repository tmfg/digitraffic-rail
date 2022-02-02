package fi.livi.rata.avoindata.common.domain.cause;

import static fi.livi.rata.avoindata.common.domain.cause.Cause.causeOidToNumber;

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
import javax.persistence.Transient;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Type;
import org.ietf.jgss.GSSException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Entity
@ApiModel(description = "Category code that is one step more detailed from its parent CategoryCode")
public class DetailedCategoryCode extends ACauseCode {
    @Id
    @JsonIgnore
    public String oid;

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
    @JoinColumn(name = "category_code_oid", referencedColumnName = "oid", nullable = false)
    @JsonIgnore
    public CategoryCode categoryCode;

    @OneToMany(mappedBy = "detailedCategoryCode", fetch = FetchType.LAZY)
    @OrderBy
    @JsonIgnore
    public Set<ThirdCategoryCode> thirdCategoryCodes = new HashSet<>();

    @Transient
    public Integer getId() throws GSSException {
        return causeOidToNumber(this.oid);
    }

    @Override
    public String getIdString() {
        return oid;
    }
}
