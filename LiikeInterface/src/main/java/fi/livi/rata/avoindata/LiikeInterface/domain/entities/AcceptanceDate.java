package fi.livi.rata.avoindata.LiikeInterface.domain.entities;


import org.hibernate.annotations.Type;

import java.time.ZonedDateTime;

public class AcceptanceDate {
    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime hyvaksymisaika;

    public String junanumero;
}
