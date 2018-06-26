package fi.livi.rata.avoindata.LiikeInterface.domain.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@Table(name = "kp_vaunu")
public class Vaunu {
    private static final int KAHVIO = 0;
    private static final int LEIKKITILA = 1;
    private static final int LEMMIKKIELAINOSASTO = 2;
    private static final int PYORATUOLIPAIKKA = 3;
    private static final int TILAA_MATKALAUKUILLE = 4;
    private static final int TUPAKOINTI = 5;
    private static final int VIDEO = 6;

    public static final String KEY_NAME = "kpva_id";

    @Id
    @Column(name = KEY_NAME)
    public Long id;

    public String myyntinumero;

    public Integer sijainti;

    public Integer pituus;

    @JsonIgnore
    public Integer palvelut;

    @ManyToOne
    @JoinColumn(name = Kokoonpano.KEY_NAME)
    private Kokoonpano kokoonpano;

    @ManyToOne
    @JoinColumn(name = "tunniste", referencedColumnName = "KALUSTOYKSIKKONRO")
    public Kalustoyksikko kalustoyksikko;

    private boolean isSet(final int bit) {
        return (palvelut & (1 << bit)) > 0;
    }

    public boolean getKahvio() {
        return isSet(KAHVIO);
    }

    public boolean getLeikkitila() {
        return isSet(LEIKKITILA);
    }

    public boolean getLemmikkielainosasto() {
        return isSet(LEMMIKKIELAINOSASTO);
    }

    public boolean getPyoratuolipaikka() {
        return isSet(PYORATUOLIPAIKKA);
    }

    public boolean getTilaaMatkalaukuille() {
        return isSet(TILAA_MATKALAUKUILLE);
    }

    public boolean getTupakointi() {
        return isSet(TUPAKOINTI);
    }

    public boolean getVideo() {
        return isSet(VIDEO);
    }

    public int getPalvelut() {
        return palvelut;
    }

}