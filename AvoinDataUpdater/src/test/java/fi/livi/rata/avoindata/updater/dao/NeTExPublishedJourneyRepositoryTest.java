package fi.livi.rata.avoindata.updater.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourneyTrack;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;

/**
 * DB round-trip coverage for {@link NeTExPublishedJourney#tracks}' ordering: JPA's {@code @OrderBy} is only
 * meaningful once the entity is reloaded from the database (an in-memory list built via
 * {@link NeTExPublishedJourney#addTrack} keeps plain insertion order regardless of the annotation), so this needs
 * a real persist + reload, unlike the mock-based {@code NeTExPublishedJourneyWriterTest}.
 */
@Transactional
public class NeTExPublishedJourneyRepositoryTest extends BaseTest {
    @Autowired
    private NeTExPublishedJourneyRepository publishedJourneyRepository;

    @Autowired
    private EntityManager entityManager;

    // Regression test for a bug where @OrderBy("visitIndex ASC") was used instead of a dedicated sequence field:
    // visitIndex is a per-station occurrence counter (0 for every station's first visit), not the track's
    // position in the journey, so ordering by it moved a repeated station's second visit to the end of the list
    // instead of keeping it at its true journey position - misplacing tracks.getFirst()/getLast() (used to
    // derive SIRI-VM's OriginRef/DestinationRef) whenever a journey revisits a station.
    @Test
    public void tracksAreReloadedInJourneyOrderNotVisitIndexOrder() {
        final LocalDate departureDate = DateProvider.dateInHelsinki();
        final NeTExPublishedJourney journey = new NeTExPublishedJourney(new TrainId(59L, departureDate),
                "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr", "FTR:JourneyPattern:59", 1L,
                DateProvider.nowInHelsinki());
        // HKI -> TPE -> HKI (revisited) -> OL, in that journey order. TPE and OL each have visitIndex 0 (their
        // only visit); HKI has visitIndex 0 then 1. Ordering by visitIndex alone would put HKI's second visit
        // (index=1) after every visitIndex=0 track, i.e. last - not its true position (third of four).
        journey.addTrack(new NeTExPublishedJourneyTrack("HKI", "7", 0, 0));
        journey.addTrack(new NeTExPublishedJourneyTrack("TPE", "1", 0, 1));
        journey.addTrack(new NeTExPublishedJourneyTrack("HKI", "2", 1, 2));
        journey.addTrack(new NeTExPublishedJourneyTrack("OL", "1", 0, 3));
        publishedJourneyRepository.persist(List.of(journey));
        publishedJourneyRepository.flush();
        entityManager.clear();

        final List<NeTExPublishedJourney> reloaded = publishedJourneyRepository
                .findByDatasetVersionAndDepartureDatesFetchTracks(1L, List.of(departureDate));

        assertThat(reloaded).hasSize(1);
        final List<NeTExPublishedJourneyTrack> tracks = reloaded.get(0).tracks;
        assertThat(tracks).extracting(t -> t.stationShortCode).containsExactly("HKI", "TPE", "HKI", "OL");
        // The journey's true first/last commercial stop, not whichever track happens to have the lowest/highest
        // visitIndex.
        assertThat(tracks.getFirst().stationShortCode).isEqualTo("HKI");
        assertThat(tracks.getFirst().plannedTrack).isEqualTo("7");
        assertThat(tracks.getLast().stationShortCode).isEqualTo("OL");
    }
}
