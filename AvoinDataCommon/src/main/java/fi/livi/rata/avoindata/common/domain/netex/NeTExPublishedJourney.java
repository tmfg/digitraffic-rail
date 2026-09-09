package fi.livi.rata.avoindata.common.domain.netex;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

@Entity
public class NeTExPublishedJourney {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Embedded
    public TrainId trainId;
    public String serviceJourneyId;
    public String lineId;
    public String operatorRef;
    public String journeyPatternRef;
    public long datasetVersion;
    public ZonedDateTime generatedAt;

    @OneToMany(mappedBy = "journey", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<NeTExPublishedJourneyTrack> tracks = new ArrayList<>();

    public NeTExPublishedJourney() {
    }

    public NeTExPublishedJourney(final TrainId trainId, final String serviceJourneyId, final String lineId,
            final String operatorRef, final String journeyPatternRef, final long datasetVersion,
            final ZonedDateTime generatedAt) {
        this.trainId = trainId;
        this.serviceJourneyId = serviceJourneyId;
        this.lineId = lineId;
        this.operatorRef = operatorRef;
        this.journeyPatternRef = journeyPatternRef;
        this.datasetVersion = datasetVersion;
        this.generatedAt = generatedAt;
    }

    public void addTrack(final NeTExPublishedJourneyTrack track) {
        track.journey = this;
        this.tracks.add(track);
    }
}
