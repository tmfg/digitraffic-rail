package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import javax.persistence.*;

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
    @JoinColumn(name = Syyluokka.KEY_NAME)
    public Syyluokka syyluokka;

    @ManyToOne
    @JoinColumn(name = Syykoodi.KEY_NAME)
    public Syykoodi syykoodi;

    @ManyToOne
    @JoinColumn(name = TarkentavaSyykoodi.KEY_NAME)
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
