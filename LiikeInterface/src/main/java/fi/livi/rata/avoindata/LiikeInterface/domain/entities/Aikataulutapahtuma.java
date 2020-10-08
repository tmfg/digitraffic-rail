package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;

@Entity
public class Aikataulutapahtuma {
    public static final String KEY_NAME = "attap_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @Type(type = "org.hibernate.type.LocalDateTimeType")
    @JsonView({AikatauluController.class, JunapaivaController.class})
    public LocalDateTime tapahtumaAika;

    @ManyToOne
    @JoinColumn(name = Pysahdystyyppi.KEY_NAME)
    @JsonView(AikatauluController.class)
    public Pysahdystyyppi pysahdystyyppi;

    @ManyToOne
    @JoinColumn(name = Liikennepaikka.KEY_NAME)
    @OneToMany(fetch = FetchType.EAGER)
    @JsonView(JunapaivaController.class)
    public Liikennepaikka liikennepaikka;

    @Column(name = "attap_type")
    @JsonView({AikatauluController.class, JunapaivaController.class})
    public String tyyppi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Aikataulurivi.KEY_NAME)
    public Aikataulurivi aikataulurivi;
}
