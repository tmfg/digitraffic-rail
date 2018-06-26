package fi.livi.rata.avoindata.common.domain.cause;

import io.swagger.annotations.ApiModelProperty;

public class PassengerTerm {
    @ApiModelProperty("Finnish passenger friendly term for the code")
    public String fi;
    @ApiModelProperty("English passenger friendly term for the code")
    public String en;
    @ApiModelProperty("Swedish passenger friendly term for the code")
    public String sv;

    public PassengerTerm(final String fi, final String sv, final String en) {
        this.fi = fi;
        this.en = en;
        this.sv = sv;
    }
}
