package fi.livi.rata.avoindata.updater.observability;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a wide event as the space-separated {@code key=value} message that
 * {@code LoggerMessageKeyValuePairJsonProvider} turns into JSON fields. Logging the map itself
 * would produce {@code Map.toString()}, which that provider cannot parse.
 */
public final class LogFields {

    private LogFields() {
    }

    public static String of(final Map<String, Object> event) {
        return event.entrySet().stream()
                .map(field -> field.getKey() + "=" + value(field.getValue()))
                .collect(Collectors.joining(" "));
    }

    /** The provider drops blank values, so absent ones are spelled out to keep the field set stable. */
    private static String value(final Object value) {
        final String text = value == null ? "" : String.valueOf(value);
        return text.isBlank() ? "NULL" : text;
    }
}
