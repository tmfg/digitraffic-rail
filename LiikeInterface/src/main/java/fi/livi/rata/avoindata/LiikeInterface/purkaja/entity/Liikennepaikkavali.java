package fi.livi.rata.avoindata.LiikeInterface.purkaja.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Liikennepaikkavali {
    public static final String KEY_NAME = "LPVAL_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;
}
