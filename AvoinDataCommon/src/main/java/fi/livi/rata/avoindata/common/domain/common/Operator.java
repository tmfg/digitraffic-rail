package fi.livi.rata.avoindata.common.domain.common;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import io.swagger.annotations.ApiModelProperty;

@Embeddable
public class Operator {
    @Column(name = "operator_uic_code")
    @ApiModelProperty(value = "Official UIC code of the operator", example = "10")
    public int operatorUICCode;

    @Column
    @ApiModelProperty(value = "Short code of the operator", example = "vr")
    public String operatorShortCode;
}
