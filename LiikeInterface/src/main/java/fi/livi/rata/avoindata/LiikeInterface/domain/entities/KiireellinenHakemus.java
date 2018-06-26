package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class KiireellinenHakemus extends BaseEntity {
    public static final String KEY_NAME = "KIHAK_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public Boolean arkaluontoinen;

    public String hakemustila;
}
