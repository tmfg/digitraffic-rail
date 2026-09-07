package fi.livi.rata.avoindata.updater.service.siri.common;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import fi.livi.rata.avoindata.common.utils.DateProvider;

import static org.junit.jupiter.api.Assertions.*;

class SiriTimeConverterTest {

    private final SiriTimeConverter converter = new SiriTimeConverter();

    // --- TIME-01: Summer time (EEST) renders correct local ISO ---

    @Test
    void givenUtcSummerInstant_whenConvertToSiriDateTime_thenRendersHelsinkiEEST() {
        // given
        final ZonedDateTime utcInstant = ZonedDateTime.of(2026, 7, 10, 18, 22, 23, 0, ZoneOffset.UTC);

        // when
        final String result = converter.toSiriDateTime(utcInstant);

        // then — UTC+3 in summer
        assertEquals("2026-07-10T21:22:23", result);
    }

    // --- TIME-02: Winter time (EET) renders correct local ISO ---

    @Test
    void givenUtcWinterInstant_whenConvertToSiriDateTime_thenRendersHelsinkiEET() {
        // given
        final ZonedDateTime utcInstant = ZonedDateTime.of(2026, 1, 15, 16, 30, 0, 0, ZoneOffset.UTC);

        // when
        final String result = converter.toSiriDateTime(utcInstant);

        // then — UTC+2 in winter
        assertEquals("2026-01-15T18:30:00", result);
    }

    // --- TIME-03: Spring DST transition (clocks skip 03→04) produces valid wall-clock ---

    @Test
    void givenUtcDuringSpringDstGap_whenConvertToSiriDateTime_thenRendersCorrectEEST() {
        // given — 2026-03-29 01:30 UTC = during the spring-forward night
        final ZonedDateTime utcInstant = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, ZoneOffset.UTC);

        // when
        final String result = converter.toSiriDateTime(utcInstant);

        // then — 01:30 UTC = 04:30 EEST (clocks jumped from 03:00 to 04:00)
        assertEquals("2026-03-29T04:30:00", result);
    }

    // --- TIME-04: Autumn DST fall-back renders unambiguously ---

    @Test
    void givenHelsinkiDuringAutumnOverlap_whenConvertToSiriDateTime_thenRendersValidIsoWithoutOffset() {
        // given — 00:30 Helsinki on fall-back night (overlap period)
        final ZonedDateTime helsinkiInstant = ZonedDateTime.of(2026, 10, 25, 0, 30, 0, 0,
                DateProvider.ZONE_ID_HKI);

        // when
        final String result = converter.toSiriDateTime(helsinkiInstant);

        // then — valid ISO local time, seconds granularity, no offset suffix
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                "Expected ISO local date-time without offset, got: " + result);
        assertFalse(result.contains("Z"));
        assertFalse(result.contains("+"));
    }

    // --- TIME-05: Midnight boundary has seconds granularity ---

    @Test
    void givenUtc21July10_whenConvertToSiriDateTime_thenRendersMidnightHelsinki() {
        // given — 21:00 UTC = 00:00 Helsinki next day
        final ZonedDateTime utcInstant = ZonedDateTime.of(2026, 7, 10, 21, 0, 0, 0, ZoneOffset.UTC);

        // when
        final String result = converter.toSiriDateTime(utcInstant);

        // then
        assertEquals("2026-07-11T00:00:00", result);
    }

    // --- TIME-06: Null input returns null ---

    @Test
    void givenNullInstant_whenConvertToSiriDateTime_thenReturnsNull() {
        // given / when
        final String result = converter.toSiriDateTime(null);

        // then
        assertNull(result);
    }

    // --- TIME-07: Duration renders xsd:duration (including PT0S) ---

    @Test
    void givenZeroDuration_whenConvertToSiriDuration_thenReturnsPT0S() {
        // given
        final Duration zero = Duration.ZERO;

        // when
        final String result = converter.toSiriDuration(zero);

        // then
        assertEquals("PT0S", result);
    }

    @Test
    void givenNonZeroDuration_whenConvertToSiriDuration_thenReturnsIsoDuration() {
        // given
        final Duration threeMinutes45Seconds = Duration.ofMinutes(3).plusSeconds(45);

        // when
        final String result = converter.toSiriDuration(threeMinutes45Seconds);

        // then
        assertEquals("PT3M45S", result);
    }
}
