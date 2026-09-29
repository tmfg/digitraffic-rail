package fi.livi.rata.avoindata.updater.service.gtfs;

import java.util.HashSet;
import java.util.Set;

/**
 * Segments whose route lookup already failed during this run. The remaining feeds reuse the failure
 * instead of re-requesting it, so one broken segment costs the Infra API one request per run rather
 * than one per feed.
 * <p>
 * Not thread safe: one instance belongs to one run.
 */
public class FailedSegments {
    private final Set<String> segments = new HashSet<>();

    public static String key(final String startStopId, final String endStopId) {
        return "%s->%s".formatted(startStopId, endStopId);
    }

    public boolean contains(final String segment) {
        return segments.contains(segment);
    }

    public void record(final String segment) {
        segments.add(segment);
    }
}
