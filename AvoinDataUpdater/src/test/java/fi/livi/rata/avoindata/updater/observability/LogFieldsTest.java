package fi.livi.rata.avoindata.updater.observability;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The log provider reads {@code key=value} pairs out of the message, so these rules decide whether the
 * {@code rail.*} fields end up indexed.
 */
class LogFieldsTest {

    @Test
    void givenAnEventWhenRenderedThenPairsAreSpaceSeparated() {
        // Given
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("operation", "generateGtfs");
        event.put("rail.gtfs.segments.dummy", 3);
        event.put("rail.gtfs.feed.published", true);

        // When / Then
        assertThat(LogFields.of(event))
                .isEqualTo("operation=generateGtfs rail.gtfs.segments.dummy=3 rail.gtfs.feed.published=true");
    }

    @Test
    void givenAbsentValuesWhenRenderedThenTheFieldSurvivesAsNull() {
        // Given success and error events must share one field set
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("error.type", "");
        event.put("rail.gtfs.feeds.degraded", null);

        // When / Then a blank value would otherwise be dropped by the provider
        assertThat(LogFields.of(event)).isEqualTo("error.type=NULL rail.gtfs.feeds.degraded=NULL");
    }

    @Test
    void givenValuesWithSeparatorsWhenRenderedThenTheyArePassedThroughVerbatim() {
        // Given a value with spaces, which the provider keeps only up to the next space
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("error.message", "too many concurrent operations");
        event.put("url.full", "https://example.invalid/reitit?time=now");

        // When / Then the field may extract partially, but the text stays intact and searchable
        assertThat(LogFields.of(event))
                .isEqualTo("error.message=too many concurrent operations url.full=https://example.invalid/reitit?time=now");
    }

    @Test
    void givenMillisecondsWhenRenderedAsDurationThenTheValueIsInSeconds() {
        // Given OpenTelemetry expects a duration in seconds
        assertThat(LogFields.durationSeconds(1234)).isEqualTo(1.234);
        assertThat(LogFields.durationSeconds(0)).isEqualTo(0.0);
        assertThat(LogFields.durationSeconds(1)).isEqualTo(0.001);
        assertThat(LogFields.durationSeconds(60_000)).isEqualTo(60.0);
    }

    @Test
    void givenADurationWhenRenderedThenItKeepsADecimalPointInEveryLocale() {
        // Given a comma would be read as a grouping separator and turn 1,4 into 14
        assertThat(String.valueOf(LogFields.durationSeconds(1400))).isEqualTo("1.4");
    }
}
