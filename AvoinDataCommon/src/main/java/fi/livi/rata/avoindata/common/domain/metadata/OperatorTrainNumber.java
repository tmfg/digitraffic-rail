package fi.livi.rata.avoindata.common.domain.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.*;
import java.io.Serializable;

@Table
@Entity
@JsonPropertyOrder({"id", "bottomLimit", "topLimit", "trainCategory"})
@ApiModel(description = "Operators own a range of train numbers")
public class OperatorTrainNumber implements Serializable {
    @Id
    @Column
    public Long id;
    @ApiModelProperty(value = "Where operator's train numbers start", example = "76050")
    public int bottomLimit;
    @ApiModelProperty(value = "Where operator's train numbers end", example = "76219")
    public int topLimit;
    @ApiModelProperty(example = "On-track machines")
    public String trainCategory;

    @ManyToOne
    @JsonIgnore
    public Operator operator;
}
