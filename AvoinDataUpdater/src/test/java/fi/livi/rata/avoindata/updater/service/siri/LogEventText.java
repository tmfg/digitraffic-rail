package fi.livi.rata.avoindata.updater.service.siri;

import java.util.stream.Collectors;

import ch.qos.logback.classic.spi.ILoggingEvent;

/** Renders the key-value pairs of a log event as {@code key=value} text for assertions. */
public final class LogEventText {

    private LogEventText() {
    }

    public static String of(final ILoggingEvent event) {
        return event.getKeyValuePairs() == null ? "" : event.getKeyValuePairs().stream()
                .map(kv -> kv.key + "=" + kv.value)
                .collect(Collectors.joining(" "));
    }
}
