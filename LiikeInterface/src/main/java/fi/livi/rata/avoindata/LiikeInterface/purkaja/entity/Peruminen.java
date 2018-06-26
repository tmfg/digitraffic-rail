package fi.livi.rata.avoindata.LiikeInterface.purkaja.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Aikataulu;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Aikataulurivi;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.time.LocalDate;
import java.util.List;

@Entity
public class Peruminen {
    public static final String KEY_NAME = "peru_id";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @ManyToOne
    @JoinColumn(name = Aikataulu.KEY_NAME)
    @JsonIgnore
    public Aikataulu aikataulu;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate alkuPvm;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate loppuPvm;

    public String peruType;

    @ManyToMany
    @JoinTable(name = "osavaliperuttu_rivi",  joinColumns = {
            @JoinColumn(name = Peruminen.KEY_NAME) },
            inverseJoinColumns = { @JoinColumn(name = Aikataulurivi.KEY_NAME) })
    public List<Aikataulurivi> aikataulurivis;
}
