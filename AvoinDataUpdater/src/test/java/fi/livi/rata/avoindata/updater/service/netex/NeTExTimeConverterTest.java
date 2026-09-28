package fi.livi.rata.avoindata.updater.service.netex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for NeTExTimeConverter — schedule times to NeTEx Nordic time strings.
 */
class NeTExTimeConverterTest {

    private NeTExTimeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new NeTExTimeConverter();
    }

    // --- Schedule time is already Helsinki local, so it must not be shifted ---

    @Test
    void givenScheduleTime_whenConverting_thenPublishedUnchanged() {
        // given: train 8059 departs Helsinki at 08:53 local, as the /trains API publishes it
        final Duration departure = Duration.ofHours(8).plusMinutes(53);

        // when
        final String result = converter.toNeTExTime(departure, departure);

        // then: no timezone offset is applied
        assertEquals("08:53:00", result);
    }

    @Test
    void givenSameTimeInWinterAndSummer_whenConverting_thenSameOutput() {
        // given: the schedule carries a wall-clock time, so the season cannot change it
        final Duration departure = Duration.ofHours(16).plusMinutes(30);

        // when
        final String result = converter.toNeTExTime(departure, departure);

        // then
        assertEquals("16:30:00", result);
    }

    @Test
    void givenScheduleTimeWithSeconds_whenConverting_thenSecondsKept() {
        final Duration arrival = Duration.ofHours(8).plusMinutes(57).plusSeconds(6);

        final String result = converter.toNeTExTime(arrival, Duration.ofHours(8).plusMinutes(53));

        assertEquals("08:57:06", result);
    }

    @Test
    void givenStopPastMidnight_whenConverting_thenUsesGreaterThan24Notation() {
        // given: departs 22:00, stops again at 00:30 the next day
        final Duration firstDeparture = Duration.ofHours(22);
        final Duration stop = Duration.ofHours(24).plusMinutes(30);

        // when
        final String result = converter.toNeTExTime(stop, firstDeparture);

        // then
        assertEquals("24:30:00", result);
    }

    @Test
    void givenDurationPastMidnight_whenMappingToLocalTime_thenWrapsToTimeOfDay() {
        final LocalTime result = converter.toLocalTime(Duration.ofHours(25).plusMinutes(15));

        assertEquals(LocalTime.of(1, 15), result);
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
    void givenNightTrain_whenStopIsAfterMidnight_thenBothTimesFormatted() {
        // given: departs 23:50, next stop 00:10 the following day
        final Duration departure = Duration.ofHours(23).plusMinutes(50);
        final Duration arrival = Duration.ofHours(24).plusMinutes(10);

        // when
        final String departureStr = converter.toNeTExTime(departure, departure);
        final String arrivalStr = converter.toNeTExTime(arrival, departure);

        // then
        assertEquals("23:50:00", departureStr);
        assertEquals("24:10:00", arrivalStr);
    }

    @Test
    void givenLateNightTrain_whenStopAfterMidnight_thenRollsOver() {
        // given: train departs at 22:00 local, has a stop at 00:30 local (past
        // midnight)
        final LocalTime stopTime = LocalTime.of(0, 30);
        final LocalTime firstDeparture = LocalTime.of(22, 0);

        // when
        final String result = converter.formatNeTExTime(stopTime, firstDeparture);

        // then: 00:30 past midnight = 24:30:00
        assertEquals("24:30:00", result);
    }
}
