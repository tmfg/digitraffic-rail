package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.util.List;

/**
 * Supplies PETI stop data to NeTEx generation.
 */
public interface PetiStopSource {

    /** Return the current list of stops. May be empty (no data loaded yet). */
    List<PetiStop> getStops();

    /**
     * Ensure stop data is available before generation. Static/disabled sources are
     * a no-op
     * (an empty result is intentional); a live feed loads on demand and fails if it
     * cannot
     * supply data, so generation never silently ships a package without stop
     * assignments.
     */
    default void ensureLoaded() {
    }

    /** Convenience: build a matcher from current stops. */
    default PetiUicMatcher getMatcher() {
        return new PetiUicMatcher(getStops());
    }

    /** Age of the current snapshot in seconds, or -1 when never loaded / not applicable (static sources). */
    default long getSnapshotAgeSeconds() {
        return -1L;
    }
}
