package fi.livi.rata.avoindata.common.domain.cause;

import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Entity
@ApiModel(description = "Most detailed category code for a Cause")
public class ThirdCategoryCode extends ACauseCode {
    @Id
    @JsonView(CategoryCodeJsonView.All.class)
    public Long id;

    @JsonView({CategoryCodeJsonView.OnlyCauseCategoryCodes.class, CategoryCodeJsonView.All.class})
    @Column(name = "code")
    @ApiModelProperty(example = "E103")
    public String thirdCategoryCode;

    @ColumnDefault("")
    @Column(name = "name")
    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(example = "Valmistuminen etuajassa")
    public String thirdCategoryName;

    @JsonView(CategoryCodeJsonView.All.class)
    @ApiModelProperty(value = "Detailed description", example = "Jos kyseessä ei ole kääntyvä juna ja se on valmis lähtemään etuajassa. Esim. vaihtotöitä on selkeästi suunniteltua vähemmän.")
    public String description;

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
    @JoinColumn(name = "detailed_category_code_id", referencedColumnName = "id", nullable = false)
    @JsonIgnore
    public DetailedCategoryCode detailedCategoryCode;

    @Override
    public String getIdString() {
        return String.format("%s_%s", thirdCategoryCode, id);
    }
}
