package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Pysahdystyyppi {
    public static final String KEY_NAME = "PYST_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;
}
