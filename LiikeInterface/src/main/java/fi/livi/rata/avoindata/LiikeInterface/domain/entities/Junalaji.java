package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.Set;

@Entity
public class Junalaji {
    public static final String KEY_NAME = "JLAJI_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @JsonIgnore
    public String nimi;

    @JsonProperty("nimi")
    public String avoinDataNimi;

    @JsonIgnore
    public boolean avoinDataKokoonpanot;

    @OneToMany(mappedBy = "junalaji")
    @JsonIgnore
    public Set<Junatyyppi> junatyyppi;
}
