package fi.livi.rata.avoindata.common.domain.rami;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Station of a message. For a station message this is where the message applies. For a train message these are the
 * stations where the message is shown (for example remaining stops), not necessarily an affected stretch.
 */
@Entity
@Immutable
@Table(name = "rami_message_station")
public class RamiMessageStation {
    @Id
    public Long id;

    /**
     * Station short code as in {@code Station.shortCode}, upper case, 2 to 4 characters (for example {@code HKI},
     * {@code TPE}, {@code RI}, {@code OLLI}). Not the UIC code (for example HKI is UIC 1) and not a PETI id. The code is
     * not linked to the station table, and may also be a stopping point ({@code KRS}) or a turnout ({@code KTN}).
     */
    public String stationShortCode;
}
