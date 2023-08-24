package fi.livi.rata.avoindata.common.domain.common;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import com.fasterxml.jackson.annotation.JsonIgnore;

@MappedSuperclass
public class IdentitifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JsonIgnore
    public Long id;

    public Boolean idEquals(IdentitifiedEntity other) {
        return this.id.equals(other.id);
    }

    @Override
    public String toString() {
        return "IdentitifiedEntity{" +
                "id=" + id +
                '}';
    }
}