package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Matches Digitraffic station UIC codes to PETI StopPlaces using the rule:
 * peti.uicCode == 1_000_000 + station.uicCode (equivalently: stationUIC == petiUIC mod 100_000).
 *
 * When multiple StopPlaces share a uicCode, prefers the parent (IS_PARENT_STOP_PLACE=true).
 */
public class PetiUicMatcher {

    private final Map<Integer, PetiStop> stopsByNationalUic;
    private int unmatchedCount;

    /**
     * Build a matcher from a list of parsed PetiStops.
     *
     * @param petiStops list of parsed PETI stops
     */
    public PetiUicMatcher(final List<PetiStop> petiStops) {
        this.stopsByNationalUic = new HashMap<>();
        this.unmatchedCount = 0;

        for (final PetiStop stop : petiStops) {
            final int nationalUic = stop.uicCode() % 100_000;
            final PetiStop existing = stopsByNationalUic.get(nationalUic);
            if (existing == null) {
                stopsByNationalUic.put(nationalUic, stop);
            } else if (stop.parentStopPlace() && !existing.parentStopPlace()) {
                stopsByNationalUic.put(nationalUic, stop);
            }
        }
    }

    /**
     * Look up the PetiStop matching the given national station UIC code.
     *
     * @param stationUicCode national UIC code (e.g. 361 for Tervola)
     * @return matching PetiStop, or empty if no match
     */
    public Optional<PetiStop> match(final int stationUicCode) {
        final PetiStop result = stopsByNationalUic.get(stationUicCode);
        if (result == null) {
            unmatchedCount++;
        }
        return Optional.ofNullable(result);
    }

    /**
     * @return number of PetiStops that were successfully indexed (have valid normalized UIC keys)
     */
    public int matchedCount() {
        return stopsByNationalUic.size();
    }

    /**
     * @return number of lookup attempts that returned empty
     */
    public int unmatchedCount() {
        return unmatchedCount;
    }
}
