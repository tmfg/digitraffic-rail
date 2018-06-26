package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "UUSIN_KALUSTOYKSIKKO")
public class Kalustoyksikko extends BaseEntity {
    public static final String KEY_NAME = "KALUK_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String kalustoyksikkonro;

    public String sarjatunnus;
}
