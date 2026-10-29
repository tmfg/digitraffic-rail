package fi.livi.rata.avoindata.common.domain.rami;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Passenger information message (shown on displays and spoken in announcements). Each edit adds a new version row;
 * only the row with the highest version is current. The table is filled by another system, so this is read only.
 * The database columns are DATETIME in UTC; the connection setting {@code serverTimezone=UTC} makes them read as UTC.
 */
@Entity
@Immutable
@Table(name = "rami_message")
@IdClass(RamiMessageKey.class)
public class RamiMessage {
    public enum MessageType {
        SCHEDULED_MESSAGE,
        MONITORED_JOURNEY_SCHEDULED_MESSAGE
    }

    /** Starts with {@code SHM} for station messages (e.g. {@code SHM20260901123456789}) and {@code MVM} for train messages. */
    @Id
    public String id;

    /** Starts from 1, grows by one on every edit. */
    @Id
    public Integer version;

    @Enumerated(EnumType.STRING)
    /** Station messages have no train number or departure date; train messages always have both. */
    public MessageType messageType;

    /** Creation time of this version in the source system (not of the first version). */
    public Instant createdSource;

    public Instant startValidity;

    public Instant endValidity;

    /** Train messages only, for example 145. Null for station messages. */
    public Integer trainNumber;

    /** Train messages only. Null for station messages. */
    public LocalDate trainDepartureDate;

    /** Time the message was removed; null while the message is in use. */
    public Instant deleted;

    @OneToMany
    @JoinColumns({
            @JoinColumn(name = "rami_message_id", referencedColumnName = "id", insertable = false, updatable = false),
            @JoinColumn(name = "rami_message_version", referencedColumnName = "version", insertable = false, updatable = false)
    })
    /** At most one row per version. */
    public Set<RamiMessageVideo> videos = new HashSet<>();

    @OneToMany
    @JoinColumns({
            @JoinColumn(name = "rami_message_id", referencedColumnName = "id", insertable = false, updatable = false),
            @JoinColumn(name = "rami_message_version", referencedColumnName = "version", insertable = false, updatable = false)
    })
    /** At most one row per version. */
    public Set<RamiMessageAudio> audios = new HashSet<>();

    @OneToMany
    @JoinColumns({
            @JoinColumn(name = "rami_message_id", referencedColumnName = "id", insertable = false, updatable = false),
            @JoinColumn(name = "rami_message_version", referencedColumnName = "version", insertable = false, updatable = false)
    })
    /** One or more stations per message. */
    public Set<RamiMessageStation> stations = new HashSet<>();

    /**
     * A message about one train, as opposed to a message about stations.
     */
    public boolean isTrainMessage() {
        return messageType == MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE;
    }

    /**
     * True when both the train number and the departure date are set.
     */
    public boolean hasTrainDefined() {
        return trainNumber != null && trainDepartureDate != null;
    }
}
