package fi.livi.rata.avoindata.common.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.swagger.v3.oas.annotations.media.Schema;

@Embeddable
// Class has same name as fi.livi.rata.avoindata.common.domain.metadata.Operator.
// Name needs to be set for schema, or this is shown in OpenAPI schemas instead (even when this is hidden)
@Schema(hidden = true, name = "EmbeddedOperator")
public class Operator {
    @Column(name = "operator_uic_code")
    @Schema(description = "Official UIC code of the operator", example = "10")
    public int operatorUICCode;

    @Column
    @Schema(description = "Short code of the operator", example = "vr")
    public String operatorShortCode;

    public Operator() {
        // For hibernate
    }

    public Operator(final int operatorUICCode, final String operatorShortCode) {
        this.operatorUICCode = operatorUICCode;
        this.operatorShortCode = operatorShortCode;
    }

    @Override
    public String toString() {
        return "Operator{" +
                "operatorUICCode=" + operatorUICCode +
                ", operatorShortCode='" + operatorShortCode + '\'' +
                '}';
    }
}
