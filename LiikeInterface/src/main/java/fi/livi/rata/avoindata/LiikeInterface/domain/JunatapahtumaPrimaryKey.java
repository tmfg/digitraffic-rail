package fi.livi.rata.avoindata.LiikeInterface.domain;


import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
public class JunatapahtumaPrimaryKey implements Serializable {
    private String junanumero;

    @Type(type="org.hibernate.type.LocalDateType")
    public LocalDate lahtopvm;

    @Column(name = "attap_id")
    private Long id;


    @Override
    public boolean equals(final Object obj) {
        if (super.equals(obj)) {
            return true;
        }

        JunatapahtumaPrimaryKey other = (JunatapahtumaPrimaryKey) obj;

        if (other == null) {
            return false;
        }

        return junanumero.equals(other.junanumero) && lahtopvm.equals(other.lahtopvm) && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public String getJunanumero() {
        return junanumero;
    }

    public LocalDate getLahtopvm() {
        return lahtopvm;
    }

    public Long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "JunatapahtumaPrimaryKey{" +
                "junanumero='" + junanumero + '\'' +
                ", lahtopvm=" + lahtopvm +
                ", id=" + id +
                '}';
    }
}
