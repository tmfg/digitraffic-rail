package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.purkaja.AikatauluController;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "poikkeus_paiva")
public class Poikkeuspaiva extends BaseEntity {
    public static final String KEY_NAME = "poik_id";

    @Id
    @Column(name = KEY_NAME)
    @JsonView({AikatauluController.class})
    public Long id;

    @JsonView({AikatauluController.class})
    public Boolean ajetaan;

    @Type(type = "org.hibernate.type.LocalDateType")
    @JsonView({AikatauluController.class})
    public LocalDate pvm;

    @ManyToOne
    @JoinColumn(name = Aikataulu.KEY_NAME)
    @JsonIgnore
    public Aikataulu aikataulu;
}
