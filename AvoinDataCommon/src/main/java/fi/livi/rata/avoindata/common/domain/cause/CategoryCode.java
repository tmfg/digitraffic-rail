package fi.livi.rata.avoindata.common.domain.cause;

import static fi.livi.rata.avoindata.common.domain.cause.Cause.causeOidToNumber;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;

import org.ietf.jgss.GSSException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "CategoryCode", title = "CategoryCode", description="A code that is used to categorize reasons for a train not being on schedule")
public class CategoryCode extends ACauseCode {
    @Id
    @JsonIgnore
    public String oid;

    @JsonView({CategoryCodeJsonView.OnlyCauseCategoryCodes.class, CategoryCodeJsonView.All.class})
    @Schema(description = "Official code",example = "E", requiredMode = Schema.RequiredMode.REQUIRED)
    public String categoryCode;

    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "Official name",example = "Etuajassakulku",requiredMode = Schema.RequiredMode.REQUIRED)
    public String categoryName;

    @Column
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "Start date when this code is used", requiredMode = Schema.RequiredMode.REQUIRED)
    public LocalDate validFrom;

    @Column
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "End date when this code is used. Empty means category is used until further notice")
    public LocalDate validTo;

    @OneToMany(mappedBy = "categoryCode", fetch = FetchType.LAZY)
    @OrderBy
    @JsonIgnore
    public Set<DetailedCategoryCode> detailedCategoryCodes = new HashSet<>();

    @Transient
    @JsonView(CategoryCodeJsonView.All.class)
    public Integer getId() throws GSSException {
        return causeOidToNumber(this.oid);
    }

    @Override
    public String getIdString() {
        return this.oid;
    }
}
