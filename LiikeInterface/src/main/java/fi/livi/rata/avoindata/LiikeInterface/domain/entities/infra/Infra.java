package fi.livi.rata.avoindata.LiikeInterface.domain.entities.infra;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Infra extends BaseEntity {
    public static final String KEY_NAME = "INFRA_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;
}
