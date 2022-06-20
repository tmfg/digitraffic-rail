package fi.livi.rata.avoindata.common.domain.common;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import io.swagger.v3.oas.annotations.media.Schema;

@Embeddable
public class Operator {
    @Column(name = "operator_uic_code")
    @Schema(description = "Official UIC code of the operator", example = "10")
    public int operatorUICCode;

    @Column
    @Schema(description = "Short code of the operator", example = "vr")
    public String operatorShortCode;
}
