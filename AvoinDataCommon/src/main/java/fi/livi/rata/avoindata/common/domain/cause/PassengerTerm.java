package fi.livi.rata.avoindata.common.domain.cause;

import io.swagger.v3.oas.annotations.media.Schema;

public class PassengerTerm {
    @Schema(description = "Finnish passenger friendly term for the code")
    public String fi;
    @Schema(description = "English passenger friendly term for the code")
    public String en;
    @Schema(description = "Swedish passenger friendly term for the code")
    public String sv;

    public PassengerTerm(final String fi, final String sv, final String en) {
        this.fi = fi;
        this.en = en;
        this.sv = sv;
    }
}
