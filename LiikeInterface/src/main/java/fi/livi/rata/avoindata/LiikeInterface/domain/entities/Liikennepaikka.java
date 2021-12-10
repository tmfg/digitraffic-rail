package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.metadata.LiikennepaikkaController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.entity.LiikennepaikanLiikennepaikkaVali;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.Set;

@Entity
public class Liikennepaikka extends BaseEntity {
    public static final String KEY_NAME = "lp_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @JsonView({JunapaivaController.class, LiikennepaikkaController.class})
    public String nimi;
    @JsonView({JunapaivaController.class, LiikennepaikkaController.class,AikatauluController.class})
    public String lyhenne;
    @JsonView({JunapaivaController.class, LiikennepaikkaController.class,AikatauluController.class})
    public String maakoodi;
    @JsonView({JunapaivaController.class, LiikennepaikkaController.class,AikatauluController.class})
    public Integer uicKoodi;
    @JsonView({LiikennepaikkaController.class})
    public Long lptypId;
    @JsonView({LiikennepaikkaController.class})
    public String iKoordinaatti;
    @JsonView({LiikennepaikkaController.class})
    public String pKoordinaatti;
    @JsonView({LiikennepaikkaController.class})
    public Boolean matkustajaAikataulussa;

    @JsonIgnore
    public Long infraId;

    @JsonIgnore
    @OneToMany(mappedBy = "liikennepaikka")
    public Set<LiikennepaikanLiikennepaikkaVali> liikennepaikanLiikennepaikkaValis;
}
