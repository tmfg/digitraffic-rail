package fi.livi.rata.avoindata.LiikeInterface.domain.entities.localization;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Vetovoimalaji {
    public static final String KEY_NAME = "VVL_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    @JsonProperty("nimi")
    public String avoinDataNimi;

    public char lyhenne;
}
