package fi.livi.rata.avoindata.common.domain.common;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class SystemStateProperty {
    @Id
    public String id;

    public String value;
}
