package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.netex.NeTExService.NeTExDataset;
import fi.livi.rata.avoindata.updater.service.netex.PublishedJourneyDraft.PublishedTrack;

class NeTExPublishedJourneyWriterTest {

    private static final LocalDate TODAY = DateProvider.dateInHelsinki();

    private NeTExPublishedJourneyRepository journeyRepo;
    private NeTExPublishedJourneyBatchInserter inserter;
    private NeTExPublishedJourneyWriter writer;

    @BeforeEach
    void setUp() {
        journeyRepo = mock(NeTExPublishedJourneyRepository.class);
        inserter = mock(NeTExPublishedJourneyBatchInserter.class);
        writer = new NeTExPublishedJourneyWriter(journeyRepo, inserter, true);
    }

    // Passes the pre-joined drafts (refs incl. journeyPatternRef, and tracks) straight to the batch inserter under
    // the next dataset version.
    @Test
    void givenDataset_whenPersistWindow_thenInsertsDraftsUnderNextVersion() {
        final PublishedJourneyDraft draft = new PublishedJourneyDraft(
                new TrainId(59L, TODAY), "FTR:ServiceJourney:59-12345", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:1", List.of(new PublishedTrack("HKI", "5", 0, 0)));
        when(journeyRepo.getMaxDatasetVersion()).thenReturn(4L);

        writer.persistWindow(dataset(List.of(draft)));

        final List<PublishedJourneyDraft> inserted = insertedDrafts(5L);
        assertEquals(List.of(draft), inserted);
    }

    // The writer stores the drafts of the dataset's own window even if today has moved on since the drafts were
    // built (e.g. the run crossed midnight), so the last day of the window is not lost.
    @Test
    void givenWindowFixedInDataset_whenPersistWindow_thenStoresAllDraftsRegardlessOfToday() {
        final LocalDate lastDay = TODAY.minusDays(30);
        final PublishedJourneyDraft draft = new PublishedJourneyDraft(
                new TrainId(59L, lastDay), "FTR:ServiceJourney:59-1", "FTR:Line:IC", "FTR:Operator:vr", "jp",
                List.of());
        final NeTExDataset dataset = new NeTExDataset(List.of(), List.of(draft),
                PublishedJourneyWindow.of(lastDay, 0, 0), null, null, List.of(), List.of(), List.of(), null,
                List.of());

        writer.persistWindow(dataset);

        assertEquals(List.of(draft), insertedDrafts(1L));
    }

    private List<PublishedJourneyDraft> insertedDrafts(final long expectedVersion) {
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<PublishedJourneyDraft>> captor = ArgumentCaptor.forClass(List.class);
        verify(inserter).insert(captor.capture(), eq(expectedVersion), any());
        return captor.getValue();
    }

    // When disabled, nothing is read or written.
    @Test
    void givenDisabled_whenPersistWindow_thenNoop() {
        final NeTExPublishedJourneyWriter disabled = new NeTExPublishedJourneyWriter(journeyRepo, inserter, false);

        disabled.persistWindow(dataset(List.of()));

        verify(inserter, never()).insert(any(), anyLong(), any());
    }

    private static NeTExDataset dataset(final List<PublishedJourneyDraft> drafts) {
        return new NeTExDataset(List.of(), drafts, PublishedJourneyWindow.of(TODAY, 2, 2), null, null, List.of(),
                List.of(), List.of(), null, List.of());
    }
}
