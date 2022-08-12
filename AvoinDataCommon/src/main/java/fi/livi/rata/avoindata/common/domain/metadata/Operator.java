package fi.livi.rata.avoindata.common.domain.metadata;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

@Table
@Entity
@JsonPropertyOrder({"id", "operatorName", "operatorShortCode", "operatorUICCode", "trainNumbers"})
@Schema(name = "Operator", title = "Operator")
public class Operator implements Serializable {
    @Id
    public Long id;

    @Column(name = "operator_uic_code")
    public int operatorUICCode;

    @Column
    public String operatorShortCode;

    @Column
    public String operatorName;

    @OneToMany(mappedBy = "operator", fetch = FetchType.EAGER)
    @OrderBy
    public Set<OperatorTrainNumber> trainNumbers = new HashSet<>();
}
