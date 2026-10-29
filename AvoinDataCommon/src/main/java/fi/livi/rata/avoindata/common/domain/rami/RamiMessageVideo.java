package fi.livi.rata.avoindata.common.domain.rami;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Text shown on displays. A language can be missing.
 */
@Entity
@Immutable
@Table(name = "rami_message_video")
public class RamiMessageVideo {
    @Id
    public Long id;

    public String textFi;

    public String textSv;

    public String textEn;
}
