package fi.livi.rata.avoindata.LiikeInterface.domain;


import org.hibernate.annotations.Type;

import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
public class JunapaivaPrimaryKey implements Serializable {
    public String junanumero;

    @Type(type = "org.hibernate.type.LocalDateType")
    public LocalDate lahtopvm;

    protected JunapaivaPrimaryKey() {
    }

    public JunapaivaPrimaryKey(final String junanumero, final LocalDate lahtopvm) {
        this.junanumero = junanumero;
        this.lahtopvm = lahtopvm;
    }


    @Override
    public boolean equals(final Object obj) {
        if (super.equals(obj)) {
            return true;
        }

        JunapaivaPrimaryKey other = (JunapaivaPrimaryKey) obj;

        if (other == null) {
            return false;
        }

        return junanumero.equals(other.junanumero) && lahtopvm.equals(other.lahtopvm);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "JunapaivaPrimaryKey{" + "junanumero='" + junanumero + '\'' + ", lahtopvm=" + lahtopvm + '}';
    }
}
