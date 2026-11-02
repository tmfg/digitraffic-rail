package fi.livi.rata.avoindata.common.domain.rami;

import java.io.Serializable;
import java.util.Objects;

public class RamiMessageKey implements Serializable {
    public String id;
    public Integer version;

    public RamiMessageKey() {
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof final RamiMessageKey other && Objects.equals(id, other.id) && Objects.equals(version, other.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }
}
