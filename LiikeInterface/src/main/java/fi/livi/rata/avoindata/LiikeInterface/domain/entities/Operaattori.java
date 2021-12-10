package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.metadata.OperaattoriController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.Set;

@Entity
public class Operaattori extends BaseEntity {
    public static final String KEY_NAME = "oper_id";

    @Id
    @Column(name = KEY_NAME)
    @JsonView({OperaattoriController.class, JunapaivaController.class})
    public Long id;

    @JsonView({OperaattoriController.class, JunapaivaController.class,AikatauluController.class})
    public Integer uicKoodi;

    @JsonView({OperaattoriController.class, JunapaivaController.class,AikatauluController.class})
    public String lyhenne;

    @JsonView({OperaattoriController.class})
    public String nimi;

    @OneToMany(mappedBy = "operaattori")
    @JsonView({OperaattoriController.class})
    public Set<OperaattorikohtainenJNS> junanumerosarjat;
}
