package fi.livi.rata.avoindata.updater.observability;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The log provider splits the message on spaces and on the first {@code =}, so these rules decide
 * whether the {@code rail.*} fields end up indexed or silently mangled.
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
    void givenValuesWithSeparatorsWhenRenderedThenTheFieldIsNotTruncated() {
        // Given
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("error.message", "too many concurrent operations");
        event.put("url.full", "https://example.invalid/reitit?time=now");

        // When / Then the value stays in one parseable token instead of losing its tail
        assertThat(LogFields.of(event))
                .isEqualTo("error.message=too_many_concurrent_operations url.full=https://example.invalid/reitit?time_now");
    }
}
