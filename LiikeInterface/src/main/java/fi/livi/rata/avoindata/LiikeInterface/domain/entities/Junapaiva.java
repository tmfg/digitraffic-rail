package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;

import javax.persistence.*;
import java.util.Set;

@Entity
public class Junapaiva extends BaseEntity {
    @EmbeddedId
    public JunapaivaPrimaryKey id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = Aikataulu.KEY_NAME)
    public Aikataulu aikataulu;

    public String jupaTila;

    @OneToMany(mappedBy = "junapaiva")
    public Set<JupaTapahtuma> jupaTapahtumas;

    @Override
    public String toString() {
        return String.format("%s - %s",id.lahtopvm,id.junanumero);
    }

}
