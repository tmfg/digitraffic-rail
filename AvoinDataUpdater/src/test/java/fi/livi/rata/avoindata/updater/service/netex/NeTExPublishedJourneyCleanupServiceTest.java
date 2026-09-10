package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.utils.DateProvider;

class NeTExPublishedJourneyCleanupServiceTest {

    private final NeTExPublishedJourneyRepository journeyRepo = mock(NeTExPublishedJourneyRepository.class);

    // Deletes everything generated before now - retentionDays; the FK cascade takes care of the tracks.
    @Test
    void whenDeleteOldJourneys_thenPrunesAtRetentionCutoff() {
        final NeTExPublishedJourneyCleanupService service =
                new NeTExPublishedJourneyCleanupService(journeyRepo, 14);
        when(journeyRepo.deleteByGeneratedAtBefore(any())).thenReturn(3);

        service.deleteOldJourneys();

        final ArgumentCaptor<ZonedDateTime> cutoff = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(journeyRepo).deleteByGeneratedAtBefore(cutoff.capture());
        final long daysBack = ChronoUnit.DAYS.between(cutoff.getValue(), DateProvider.nowInHelsinki());
        assertTrue(daysBack >= 13 && daysBack <= 14, "cutoff should be ~14 days back but was " + daysBack);
    }
}
