package fi.livi.rata.avoindata.common.domain.cause;

import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.common.domain.jsonview.CategoryCodeJsonView;

public abstract class ACauseCode {
    @Transient
    @JsonView(CategoryCodeJsonView.All.class)
    public PassengerTerm passengerTerm;

    @JsonIgnore
    public abstract String getIdString();
}
