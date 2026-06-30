package fi.livi.rata.avoindata.updater.service.netex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for NeTExTimeConverter — UTC to NeTEx Nordic local time conversion.
 */
class NeTExTimeConverterTest {

    private NeTExTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new NeTExTimeConverter();
    }

    // --- UTC to Helsinki local time conversion ---

    @Test
    void givenSummerUtcTime_whenConvertingToHelsinki_thenAddsThreeHours() {
        // given: 16:30 UTC on a summer day (EEST, UTC+3)
        final Duration utcTimestamp = Duration.ofHours(16).plusMinutes(30);
        final LocalDate summerDate = LocalDate.of(2026, 7, 15);

        // when
        final LocalTime result = converter.toHelsinkiLocalTime(utcTimestamp, summerDate);

        // then: 19:30 local
        assertEquals(LocalTime.of(19, 30), result);
    }

    @Test
    void givenWinterUtcTime_whenConvertingToHelsinki_thenAddsTwoHours() {
        // given: 16:30 UTC on a winter day (EET, UTC+2)
        final Duration utcTimestamp = Duration.ofHours(16).plusMinutes(30);
        final LocalDate winterDate = LocalDate.of(2026, 1, 15);

        // when
        final LocalTime result = converter.toHelsinkiLocalTime(utcTimestamp, winterDate);

        // then: 18:30 local
        assertEquals(LocalTime.of(18, 30), result);
    }

    // --- NeTEx time formatting ---

    @Test
    void givenFirstStop_whenFormatting_thenOnlyDepartureTimeFormat() {
        // given: first stop departs at 05:30 local
        final LocalTime departureTime = LocalTime.of(5, 30);

        // when
        final String result = converter.formatNeTExTime(departureTime, departureTime);

        // then
        assertEquals("05:30:00", result);
    }

    @Test
    void givenNormalTime_whenFormatting_thenStandardFormat() {
        // given: stop at 07:15, first departure was 05:30
        final LocalTime stopTime = LocalTime.of(7, 15);
        final LocalTime firstDeparture = LocalTime.of(5, 30);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then
        assertEquals("07:15:00", result);
    }

    @Test
    void givenPastMidnightTime_whenFormatting_thenUsesGreaterThan24Notation() {
        // given: train departs at 21:00, stop at 01:30 (past midnight)
        final LocalTime stopTime = LocalTime.of(1, 30);
        final LocalTime firstDeparture = LocalTime.of(21, 0);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then: 01:30 next day = 25:30:00
        assertEquals("25:30:00", result);
    }

    @Test
    void givenExactlyMidnight_whenFormatting_thenUses24Notation() {
        // given: train departs at 23:00, stop at exactly 00:00 (midnight)
        final LocalTime stopTime = LocalTime.MIDNIGHT;
        final LocalTime firstDeparture = LocalTime.of(23, 0);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then: midnight = 24:00:00
        assertEquals("24:00:00", result);
    }

    @Test
    void givenMorningTrain_whenFormatting_thenNoFalseRollover() {
        // given: train departs at 05:00, stop at 07:00 (same day, no rollover)
        final LocalTime stopTime = LocalTime.of(7, 0);
        final LocalTime firstDeparture = LocalTime.of(5, 0);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then: normal time, NOT 31:00:00
        assertEquals("07:00:00", result);
    }

    @Test
    void givenDstSpringForward_whenConverting_thenNoLostHour() {
        // given: UTC time during spring DST transition (last Sunday of March 2026 = March 29)
        // At 01:00 UTC, Helsinki goes from EET (UTC+2) → EEST (UTC+3)
        // So 00:30 UTC = 02:30 EET, and 01:30 UTC = 04:30 EEST (skipping 03:00-04:00)
        final Duration utcTimestamp = Duration.ofHours(1).plusMinutes(30);
        final LocalDate dstDate = LocalDate.of(2026, 3, 29);

        // when
        final LocalTime result = converter.toHelsinkiLocalTime(utcTimestamp, dstDate);

        // then: 01:30 UTC on DST day = 04:30 EEST
        assertEquals(LocalTime.of(4, 30), result);
    }

    @Test
    void givenDstFallBack_whenConverting_thenNoDuplicatedHour() {
        // given: UTC time during autumn DST transition (last Sunday of October 2026 = October 25)
        // At 01:00 UTC, Helsinki goes from EEST (UTC+3) → EET (UTC+2)
        // 00:30 UTC = 03:30 EEST, 01:30 UTC = 03:30 EET (same local time shown twice, but UTC is unambiguous)
        final Duration utcTimestamp = Duration.ofHours(1).plusMinutes(30);
        final LocalDate dstDate = LocalDate.of(2026, 10, 25);

        // when
        final LocalTime result = converter.toHelsinkiLocalTime(utcTimestamp, dstDate);

        // then: 01:30 UTC after fall-back = 03:30 EET
        assertEquals(LocalTime.of(3, 30), result);
    }

    @Test
    void givenTimeFormattedResult_whenChecking_thenNoDateComponent() {
        // given: a simple time
        final LocalTime stopTime = LocalTime.of(14, 5, 0);
        final LocalTime firstDeparture = LocalTime.of(10, 0);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then: format is HH:mm:ss with zero-padding
        assertEquals("14:05:00", result);
    }

    @Test
    void givenFullConversion_whenTrainSpansMidnight_thenCorrectOutput() {
        // given: a night train departing at 21:50 UTC in summer, arriving at 22:10 UTC (next day 01:10 local)
        final Duration departure = Duration.ofHours(21).plusMinutes(50);
        final Duration arrival = Duration.ofHours(22).plusMinutes(10);
        final Duration firstDeparture = departure;
        final LocalDate summerDate = LocalDate.of(2026, 7, 15);

        // when: convert both times
        final LocalTime departureLocal = converter.toHelsinkiLocalTime(departure, summerDate);
        final LocalTime arrivalLocal = converter.toHelsinkiLocalTime(arrival, summerDate);
        final String departureStr = converter.formatNeTExTime(departureLocal, departureLocal);
        final String arrivalStr = converter.formatNeTExTime(arrivalLocal, departureLocal);

        // then: departure = 00:50 local (21:50 + 3), arrival = 01:10 local (22:10 + 3)
        // Since first departure is 00:50, and arrival 01:10 > 00:50, no rollover
        assertEquals("00:50:00", departureStr);
        assertEquals("01:10:00", arrivalStr);
    }

    @Test
    void givenLateNightTrain_whenStopAfterMidnight_thenRollsOver() {
        // given: train departs at 22:00 local, has a stop at 00:30 local (past midnight)
        final LocalTime stopTime = LocalTime.of(0, 30);
        final LocalTime firstDeparture = LocalTime.of(22, 0);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then: 00:30 past midnight = 24:30:00
        assertEquals("24:30:00", result);
    }
}
