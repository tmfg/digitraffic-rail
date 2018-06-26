package fi.livi.rata.avoindata.common.domain.common;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class SystemStateProperty {
    @Id
    public String id;

    public String value;
}
