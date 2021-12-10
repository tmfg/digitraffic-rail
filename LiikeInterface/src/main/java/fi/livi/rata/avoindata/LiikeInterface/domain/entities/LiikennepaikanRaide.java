package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class LiikennepaikanRaide {
    public static final String KEY_NAME = "LPRD_ID";

    @Id
    @Column(name = KEY_NAME)
    private String id;

    @JsonView({AikatauluController.class, JunapaivaController.class})
    public String kaupallinenNro;
}
