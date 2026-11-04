package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class PublishedJourneyWindowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Test
    void givenLookbackAndLookahead_whenOf_thenWindowCoversBothEndsInclusive() {
        final PublishedJourneyWindow window = PublishedJourneyWindow.of(TODAY, 2, 7);

        assertEquals(TODAY.minusDays(2), window.start());
        assertEquals(TODAY.plusDays(7), window.end());
        assertTrue(window.contains(TODAY.minusDays(2)));
        assertTrue(window.contains(TODAY.plusDays(7)));
        assertFalse(window.contains(TODAY.minusDays(3)));
        assertFalse(window.contains(TODAY.plusDays(8)));
    }

    @Test
    void givenLookbackNarrowerThanOperatingDay_whenOf_thenStartIsOperatingDayStart() {
        final PublishedJourneyWindow window = PublishedJourneyWindow.of(TODAY, 0, 2);

        assertEquals(OperatingDayWindow.startDate(TODAY), window.start());
    }
}
