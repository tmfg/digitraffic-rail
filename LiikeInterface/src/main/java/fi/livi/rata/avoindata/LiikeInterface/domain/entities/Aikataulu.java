package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.JunapaivaController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.entity.Aikataulujoukko;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.entity.Peruminen;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
public class Aikataulu extends BaseEntity {
    public static final String KEY_NAME = "AIKT_ID";

    @Id
    @Column(name = KEY_NAME)
    @JsonView({AikatauluController.class, JunapaivaController.class})
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Operaattori.KEY_NAME)
    @JsonView({JunapaivaController.class,AikatauluController.class})
    public Operaattori operaattori;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Junatyyppi.KEY_NAME)
    @JsonView({JunapaivaController.class,AikatauluController.class})
    public Junatyyppi junatyyppi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = LahiliikenteenLinjatunnus.KEY_NAME)
    @JsonView({JunapaivaController.class,AikatauluController.class})
    public LahiliikenteenLinjatunnus lahiliikenteenLinjatunnus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = AikataulunJunanumero.KEY_NAME)
    @JsonView({JunapaivaController.class,AikatauluController.class})
    public AikataulunJunanumero aikataulunJunanumero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = KiireellinenHakemus.KEY_NAME)
    @JsonView({JunapaivaController.class,AikatauluController.class})
    public KiireellinenHakemus kiireellinenHakemus;

    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView({JunapaivaController.class, AikatauluController.class})
    public LocalDate alkupvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView({JunapaivaController.class, AikatauluController.class})
    public LocalDate loppupvm;

    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    @Transient
    @JsonView({JunapaivaController.class,AikatauluController.class})
    public ZonedDateTime hyvaksymisaika;

    public Long getId() {
        return id;
    }

    //----------------- USED FOR TIMETABLE EXTRACTING ------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Aikataulujoukko.KEY_NAME)
    @JsonIgnore
    public Aikataulujoukko aikataulujoukko;

    @OneToMany(mappedBy = "aikataulu")
    @JsonView({AikatauluController.class})
    public List<Aikataulurivi> aikataulurivis;

    @Transient
    @JsonView({AikatauluController.class})
    public Set<Peruminen> peruminens = new HashSet<>();

    @Transient
    @JsonView({AikatauluController.class})
    public Set<Poikkeuspaiva> poikkeuspaivas = new HashSet<>();

    @JsonView({AikatauluController.class})
    private Boolean kulkuMa;
    @JsonView({AikatauluController.class})
    private Boolean kulkuTi;
    @JsonView({AikatauluController.class})
    private Boolean kulkuKe;
    @JsonView({AikatauluController.class})
    private Boolean kulkuTo;
    @JsonView({AikatauluController.class})
    private Boolean kulkuPe;
    @JsonView({AikatauluController.class})
    private Boolean kulkuLa;
    @JsonView({AikatauluController.class})
    private Boolean kulkuSu;

    @Column(name = "ORA_ROWSCN")
    @JsonView({AikatauluController.class})
    public Long version;

    @JsonView({AikatauluController.class})
    public String tyyppi;

    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView({AikatauluController.class})
    public LocalDate voimaanastumishetki;

    public String aikataulupaatos;

    public String kaiktType;

    @JsonView({AikatauluController.class})
    public String muutos;

    @JsonView({AikatauluController.class})
    public String kapasiteettiId;
}
