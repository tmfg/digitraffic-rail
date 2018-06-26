package fi.livi.rata.avoindata.LiikeInterface.domain.entities.routeset;

import fi.livi.rata.avoindata.LiikeInterface.domain.BaseEntity;

import javax.persistence.*;

@Entity
public class Routesection extends BaseEntity {
    public static final String KEY_NAME = "ROSEC_ID";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String sectionId;
    public String stationCode;
    public String commercialTrackId;
    public Integer sectionOrder;

    @ManyToOne
    @JoinColumn(name = Routeset.KEY_NAME)
    private Routeset routeset;
}
