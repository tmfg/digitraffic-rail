package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExDataset;
import fi.livi.rata.avoindata.updater.service.netex.PublishedJourneyDraft.PublishedTrack;

class NeTExPublishedJourneyWriterTest {

    private static final LocalDate TODAY = DateProvider.dateInHelsinki();

    private NeTExPublishedJourneyRepository journeyRepo;
    private NeTExPublishedJourneyWriter writer;

    @BeforeEach
    void setUp() {
        journeyRepo = mock(NeTExPublishedJourneyRepository.class);
        writer = new NeTExPublishedJourneyWriter(journeyRepo, true, 2, 2);
    }

    // Consumes the pre-joined drafts (no RIPA, no id re-join) and persists each as a journey with its planned
    // tracks as a FK aggregate, carrying the refs (incl. journeyPatternRef) straight through.
    @Test
    void givenDataset_whenPersistWindow_thenPersistsJourneyWithTracksFromDraft() {
        final PublishedJourneyDraft draft = new PublishedJourneyDraft(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:1", List.of(new PublishedTrack("HKI", "5", 0)));
        final NeTExDataset dataset = dataset(List.of(draft));

        when(journeyRepo.getMaxDatasetVersion()).thenReturn(4L);

        writer.persistWindow(dataset);

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<NeTExPublishedJourney>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(journeyRepo).persist(captor.capture());
        final List<NeTExPublishedJourney> persisted = new ArrayList<>(captor.getValue());

        assertEquals(1, persisted.size());
        final NeTExPublishedJourney journey = persisted.get(0);
        assertEquals("FTR:ServiceJourney:59-12345", journey.serviceJourneyId);
        assertEquals("FTR:Line:IC", journey.lineId);
        assertEquals("FTR:Operator:vr", journey.operatorRef);
        assertEquals("FTR:JourneyPattern:1", journey.journeyPatternRef);
        assertEquals(5L, journey.datasetVersion); // max(4) + 1
        assertEquals(TODAY, journey.trainId.departureDate);

        // The draft's track becomes a track row, back-referencing its journey.
        assertEquals(1, journey.tracks.size());
        assertEquals("HKI", journey.tracks.get(0).stationShortCode);
        assertEquals("5", journey.tracks.get(0).plannedTrack);
        assertEquals(journey, journey.tracks.get(0).journey);
    }

    // A station served twice keeps a track row per visit, each carrying its 0-based visit index.
    @Test
    void givenDraftTracksWithVisitIndex_whenPersistWindow_thenVisitIndexPersisted() {
        final PublishedJourneyDraft draft = new PublishedJourneyDraft(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:1",
                List.of(new PublishedTrack("TPE", "1", 0), new PublishedTrack("TPE", "2", 1)));
        when(journeyRepo.getMaxDatasetVersion()).thenReturn(0L);

        writer.persistWindow(dataset(List.of(draft)));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<NeTExPublishedJourney>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(journeyRepo).persist(captor.capture());
        final NeTExPublishedJourney journey = new ArrayList<>(captor.getValue()).get(0);

        assertEquals(2, journey.tracks.size());
        assertEquals("1", journey.tracks.get(0).plannedTrack);
        assertEquals(0, journey.tracks.get(0).visitIndex);
        assertEquals("2", journey.tracks.get(1).plannedTrack);
        assertEquals(1, journey.tracks.get(1).visitIndex);
    }

    // A winner outside the [today-2, today+2] window is not persisted.
    @Test
    void givenTrainOutsideWindow_whenPersistWindow_thenSkipped() {
        final PublishedJourneyDraft draft = new PublishedJourneyDraft(
                new TrainId(59L, TODAY.plusDays(10)), "FTR:ServiceJourney:59-12345", "FTR:Line:IC",
                "FTR:Operator:vr", "jp", List.of());

        writer.persistWindow(dataset(List.of(draft)));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<NeTExPublishedJourney>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(journeyRepo).persist(captor.capture());
        assertEquals(0, captor.getValue().size());
    }

    // When disabled, nothing is read or written.
    @Test
    void givenDisabled_whenPersistWindow_thenNoop() {
        final NeTExPublishedJourneyWriter disabled =
                new NeTExPublishedJourneyWriter(journeyRepo, false, 2, 2);

        disabled.persistWindow(dataset(List.of()));

        verify(journeyRepo, never()).persist(any());
    }

    private static NeTExDataset dataset(final List<PublishedJourneyDraft> drafts) {
        return new NeTExDataset(List.of(), drafts, null, null, List.of(), List.of(), List.of(), null, List.of());
    }
}
