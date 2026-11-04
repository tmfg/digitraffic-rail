package fi.livi.rata.avoindata.updater.service.netex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import fi.livi.rata.avoindata.updater.service.netex.PublishedJourneyDraft.PublishedTrack;

/**
 * Checks that rows written with JDBC batches are read back correctly through JPA, including the track to journey
 * link and the order of the tracks.
 */
@Transactional
public class NeTExPublishedJourneyBatchInserterTest extends BaseTest {

    @Autowired
    private NeTExPublishedJourneyBatchInserter inserter;

    @Autowired
    private NeTExPublishedJourneyRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void insertedRowsAreReadBackWithTheirTracks() {
        final LocalDate date = DateProvider.dateInHelsinki();
        final Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        final PublishedJourneyDraft withTracks = new PublishedJourneyDraft(new TrainId(59L, date),
                "FTR:ServiceJourney:59", "FTR:Line:IC", "FTR:Operator:vr", "FTR:JourneyPattern:59",
                List.of(new PublishedTrack("HKI", "7", 0, 0), new PublishedTrack("TPE", "1", 0, 1),
                        new PublishedTrack("HKI", "2", 1, 2)));
        final PublishedJourneyDraft withoutTracks = new PublishedJourneyDraft(new TrainId(60L, date),
                "FTR:ServiceJourney:60", "FTR:Line:IC", "FTR:Operator:vr", "FTR:JourneyPattern:60", List.of());

        inserter.insert(List.of(withTracks, withoutTracks), 7L, generatedAt);
        entityManager.clear();

        final List<NeTExPublishedJourney> journeys =
                repository.findByDatasetVersionAndDepartureDatesFetchTracks(7L, List.of(date));

        assertThat(journeys).hasSize(2);
        final NeTExPublishedJourney first = journeys.stream()
                .filter(j -> j.trainId.trainNumber == 59L).findFirst().orElseThrow();
        assertThat(first.serviceJourneyId).isEqualTo("FTR:ServiceJourney:59");
        assertThat(first.lineId).isEqualTo("FTR:Line:IC");
        assertThat(first.operatorRef).isEqualTo("FTR:Operator:vr");
        assertThat(first.journeyPatternRef).isEqualTo("FTR:JourneyPattern:59");
        assertThat(first.generatedAt).isEqualTo(generatedAt);
        assertThat(first.tracks).extracting(t -> t.stationShortCode).containsExactly("HKI", "TPE", "HKI");
        assertThat(first.tracks).extracting(t -> t.plannedTrack).containsExactly("7", "1", "2");
        assertThat(first.tracks).extracting(t -> t.visitIndex).containsExactly(0, 0, 1);
        assertThat(first.tracks).extracting(t -> t.journey.id).containsOnly(first.id);

        final NeTExPublishedJourney second = journeys.stream()
                .filter(j -> j.trainId.trainNumber == 60L).findFirst().orElseThrow();
        assertThat(second.tracks).isEmpty();
    }

    @Test
    public void emptyInputInsertsNothing() {
        inserter.insert(List.of(), 8L, Instant.now());

        assertThat(repository.findByDatasetVersionAndDepartureDates(8L, List.of(DateProvider.dateInHelsinki())))
                .isEmpty();
    }
}
