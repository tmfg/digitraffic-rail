package fi.livi.rata.avoindata.common.domain.cause;

import static fi.livi.rata.avoindata.common.domain.cause.Cause.causeOidToNumber;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import org.hibernate.annotations.ColumnDefault;
import org.ietf.jgss.GSSException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Schema(name = "ThirdCategoryCode", title = "ThirdCategoryCode", description = "Most detailed category code for a Cause")
public class ThirdCategoryCode extends ACauseCode {
    @Id
    @JsonIgnore
    public String oid;

    @JsonView({CategoryCodeJsonView.OnlyCauseCategoryCodes.class, CategoryCodeJsonView.All.class})
    @Column(name = "code")
    @Schema(example = "E103")
    public String thirdCategoryCode;

    @ColumnDefault("")
    @Column(name = "name")
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(example = "Valmistuminen etuajassa")
    public String thirdCategoryName;

    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "Detailed description", example = "Jos kyseessä ei ole kääntyvä juna ja se on valmis lähtemään etuajassa. Esim. vaihtotöitä on selkeästi suunniteltua vähemmän.")
    public String description;

    @Column
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "Start date when this category code is used", requiredMode = Schema.RequiredMode.REQUIRED)
    public LocalDate validFrom;

    @Column
    @JsonView(CategoryCodeJsonView.All.class)
    @Schema(description = "End date when this code is used. Empty means category is used until further notice")
    public LocalDate validTo;

    @ManyToOne
    @JoinColumn(name = "detailed_category_code_oid", referencedColumnName = "oid", nullable = false)
    @JsonIgnore
    public DetailedCategoryCode detailedCategoryCode;

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
