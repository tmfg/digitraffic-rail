package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunatapahtumaPrimaryKey;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.time.ZonedDateTime;

@Entity
public class JupaEnnuste extends BaseEntity {
    public static final String KEY_NAME = "JE_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String lahde;

    @Type(type="org.hibernate.type.ZonedDateTimeType")
    public ZonedDateTime ennusteaika;

    public Long poikkeama;

    @Column(name = "ORA_ROWSCN")
    public Long version;

    @JsonUnwrapped(prefix = "jp_")
    public JunatapahtumaPrimaryKey jupaTapahtumaId;
}
