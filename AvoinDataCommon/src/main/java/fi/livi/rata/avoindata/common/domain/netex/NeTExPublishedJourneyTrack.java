package fi.livi.rata.avoindata.common.domain.netex;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class NeTExPublishedJourneyTrack {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id")
    public NeTExPublishedJourney journey;
    public String stationShortCode;
    public String plannedTrack;
    /** 0-based occurrence of this station within the journey, so a station served twice keeps a track per visit.
     * This is a per-station counter, not the track's position in the journey — do not use it for ordering
     * {@link NeTExPublishedJourney#tracks} (see {@link #sequenceIndex}). */
    public int visitIndex;
    /** 0-based position of this track within the journey's own visitation order, monotonically increasing
     * regardless of station repeats. This — not {@link #visitIndex} — is what makes the first/last element of
     * {@link NeTExPublishedJourney#tracks} reliably the journey's origin/destination stop. */
    public int sequenceIndex;

    public NeTExPublishedJourneyTrack() {
    }

    public NeTExPublishedJourneyTrack(final String stationShortCode, final String plannedTrack, final int visitIndex,
            final int sequenceIndex) {
        this.stationShortCode = stationShortCode;
        this.plannedTrack = plannedTrack;
        this.visitIndex = visitIndex;
        this.sequenceIndex = sequenceIndex;
    }
}
