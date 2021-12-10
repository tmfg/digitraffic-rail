package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import org.hibernate.annotations.Type;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

@Entity
public class Raidemuutos extends BaseEntity {

    public static final String KEY_NAME = "raimu_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;


    @Type(type = "org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime luontiPvm;

    @ManyToOne
    @JoinColumn(name = Aikataulurivi.KEY_NAME)
    @OneToMany(fetch = FetchType.LAZY)
    private Aikataulurivi aikataulurivi;
}
