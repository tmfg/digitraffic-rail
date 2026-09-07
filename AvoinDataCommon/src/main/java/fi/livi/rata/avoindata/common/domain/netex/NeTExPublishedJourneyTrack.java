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
    /** 0-based occurrence of this station within the journey, so a station served twice keeps a track per visit. */
    public int visitIndex;

    public NeTExPublishedJourneyTrack() {
    }

    public NeTExPublishedJourneyTrack(final String stationShortCode, final String plannedTrack, final int visitIndex) {
        this.stationShortCode = stationShortCode;
        this.plannedTrack = plannedTrack;
        this.visitIndex = visitIndex;
    }
}
