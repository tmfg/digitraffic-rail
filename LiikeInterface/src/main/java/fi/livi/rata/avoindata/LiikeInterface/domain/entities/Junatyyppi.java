package fi.livi.rata.avoindata.LiikeInterface.domain.entities;


import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
public class Junatyyppi {
    public static final String KEY_NAME = "JTYYP_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public boolean kaupallinenJunatyyppi;

    @JsonIgnore
    public String nimi;

    @JsonProperty("nimi")
    public String avoinDataNimi;

    @JsonIgnore
    public boolean avoinData;

    @ManyToOne
    @JoinColumn(name = Junalaji.KEY_NAME)
    public Junalaji junalaji;
}
