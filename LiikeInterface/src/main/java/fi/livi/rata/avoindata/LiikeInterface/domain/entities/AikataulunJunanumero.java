package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.io.Serializable;

@Entity
public class AikataulunJunanumero implements Serializable {
    public static final String KEY_NAME = "aiktj_Id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public Integer junanumero;
}
