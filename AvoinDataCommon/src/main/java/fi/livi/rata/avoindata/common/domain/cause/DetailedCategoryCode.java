package fi.livi.rata.avoindata.common.domain.cause;

import static fi.livi.rata.avoindata.common.domain.cause.Cause.causeOidToNumber;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;

import org.hibernate.annotations.ColumnDefault;
import org.ietf.jgss.GSSException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name ="DetailedCategoryCode", title = "DetailedCategoryCode", description = "Category code that is one step more detailed from its parent CategoryCode")
public class DetailedCategoryCode extends ACauseCode {
    @Id
    @JsonIgnore
    public String oid;

    @JsonView({CategoryCodeJsonView.OnlyCauseCategoryCodes.class, CategoryCodeJsonView.All.class})
    @Schema(example = "E2")
    public String detailedCategoryCode;

    @ColumnDefault("")
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(example="Ajo- tai pysähdysajan alitus")
    public String detailedCategoryName;

    @Column
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "Start date when this category code is used", requiredMode = Schema.RequiredMode.REQUIRED)
    public LocalDate validFrom;

    @Column
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "End date when this code is used. Empty means category is used until further notice")
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
    @JsonView(CategoryCodeJsonView.All.class)
    public Integer getId() throws GSSException {
        return causeOidToNumber(this.oid);
    }

    @Override
    public String getIdString() {
        return oid;
    }
}
