package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.util.List;

/**
 * Supplies PETI stop data to NeTEx generation.
 */
public interface PetiStopSource {

    /**
     * Return the current list of stops. May be empty (no data loaded yet).
     * Implementations that load on demand (e.g. a live feed) must ensure data is
     * available before returning, so callers never need a separate load step.
     */
    List<PetiStop> getStops();

    /** Convenience: build a matcher from current stops. */
    default PetiUicMatcher getMatcher() {
        return getMatcher(getStops());
    }

    /** Build a matcher from an already-fetched stop list, avoiding a redundant {@link #getStops()} call. */
    default PetiUicMatcher getMatcher(final List<PetiStop> stops) {
        return new PetiUicMatcher(stops);
    }

    /**
     * Age of the current snapshot in seconds, or -1 when never loaded / not
     * applicable (static sources).
     */
    default long getSnapshotAgeSeconds() {
        return -1L;
    }
}
