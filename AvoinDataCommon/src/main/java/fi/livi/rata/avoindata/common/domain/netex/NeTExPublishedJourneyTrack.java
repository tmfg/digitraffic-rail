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
    /** 0-based count of how many times this station has been visited so far in the journey (a station served
     * twice gets a track for each visit). Not the track's position in the journey — use {@link #sequenceIndex}
     * for ordering {@link NeTExPublishedJourney#tracks}. SIRI-ET/VM generation ({@code PlannedTrackLookup})
     * computes this same count independently from the live train's stop list, to look up this track. */
    public int visitIndex;
    /** 0-based position of this track in the journey's visit order, always increasing even when a station
     * repeats. Unlike {@link #visitIndex}, this makes the first/last entry in {@link NeTExPublishedJourney#tracks}
     * reliably the journey's origin/destination. */
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
