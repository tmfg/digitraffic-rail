package fi.livi.rata.avoindata.common.domain.metadata;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@Table
@Entity
@JsonPropertyOrder({"id", "bottomLimit", "topLimit", "trainCategory"})
@ApiModel(description = "Operators own a range of train numbers")
public class OperatorTrainNumber implements Serializable {
    @Id
    @Column
    public Long id;
    @ApiModelProperty("Where operator's train numbers start")
    public int bottomLimit;
    @ApiModelProperty("Where operator's train numbers end")
    public int topLimit;
    @ApiModelProperty(example = "On-track machines")
    public String trainCategory;

    @ManyToOne
    @JsonIgnore
    public Operator operator;
}
