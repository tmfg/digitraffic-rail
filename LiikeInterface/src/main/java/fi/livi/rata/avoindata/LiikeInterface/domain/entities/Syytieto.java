package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinColumns;
import javax.persistence.ManyToOne;
import javax.persistence.Version;

@Entity
public class Syytieto {
    public static final String KEY_NAME = "SYYT_ID";
    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String syynSelite;

    @Version
    @Column(name = "ORA_ROWSCN")
    public Long version;

    @ManyToOne
    @JoinColumn(name = "syykoodi_oid", referencedColumnName = "oid")
    public Syykoodi syykoodi;

    @ManyToOne
    @JoinColumn(name = "tarkentava_syykoodi_oid", referencedColumnName = "oid")
    public TarkentavaSyykoodi tarkentavaSyykoodi;


    public Integer poistettu;

    @ManyToOne
    @JoinColumns(value = {
            @JoinColumn(name = "lahtopvm", referencedColumnName = "lahtopvm", insertable = false, updatable = false),
            @JoinColumn(name = "junanumero", referencedColumnName = "junanumero", insertable = false, updatable = false)})
    private Junapaiva junapaiva;

    @ManyToOne
    @JoinColumns(value = {@JoinColumn(name = "attap_id", referencedColumnName = "attap_id", insertable = false, updatable = false),
            @JoinColumn(name = "lahtopvm", referencedColumnName = "lahtopvm", insertable = false, updatable = false), @JoinColumn(name =
            "junanumero", referencedColumnName = "junanumero", insertable = false, updatable = false)})
    private JupaTapahtuma jupaTapahtuma;
}
