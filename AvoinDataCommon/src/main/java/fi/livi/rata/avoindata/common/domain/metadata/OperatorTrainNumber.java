package fi.livi.rata.avoindata.common.domain.metadata;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

@Table
@Entity
@JsonPropertyOrder({"id", "bottomLimit", "topLimit", "trainCategory"})
@Schema(description = "Operators own a range of train numbers")
public class OperatorTrainNumber implements Serializable {
    @Id
    @Column
    public Long id;
    @Schema(description = "Where operator's train numbers start", example = "76050")
    public int bottomLimit;
    @Schema(description = "Where operator's train numbers end", example = "76219")
    public int topLimit;
    @Schema(example = "On-track machines")
    public String trainCategory;

    @ManyToOne
    @JsonIgnore
    public Operator operator;
}
