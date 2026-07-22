package fi.livi.rata.avoindata.updater.service.netex;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Holds the stop-related NeTEx data produced by NeTExStopsService.
 */
public class NeTExStopsData {

    private final List<NeTExScheduledStopPoint> scheduledStopPoints;
    private final List<NeTExRoutePoint> routePoints;
    private final List<NeTExDestinationDisplay> destinationDisplays;
    private final List<NeTExStopAssignment> stopAssignments;
    private final int matchedCount;
    private final int unmatchedCount;

    public NeTExStopsData(
            final List<NeTExScheduledStopPoint> scheduledStopPoints,
            final List<NeTExRoutePoint> routePoints,
            final List<NeTExDestinationDisplay> destinationDisplays) {
        this(scheduledStopPoints, routePoints, destinationDisplays, List.of(), 0, 0);
    }

    public NeTExStopsData(
            final List<NeTExScheduledStopPoint> scheduledStopPoints,
            final List<NeTExRoutePoint> routePoints,
            final List<NeTExDestinationDisplay> destinationDisplays,
            final List<NeTExStopAssignment> stopAssignments,
            final int matchedCount,
            final int unmatchedCount) {
        this.scheduledStopPoints = scheduledStopPoints;
        this.routePoints = routePoints;
        this.destinationDisplays = destinationDisplays;
        this.stopAssignments = stopAssignments;
        this.matchedCount = matchedCount;
        this.unmatchedCount = unmatchedCount;
    }

    public List<NeTExScheduledStopPoint> getScheduledStopPoints() {
        return scheduledStopPoints;
    }

    public List<NeTExRoutePoint> getRoutePoints() {
        return routePoints;
    }

    public List<NeTExDestinationDisplay> getDestinationDisplays() {
        return destinationDisplays;
    }

    public List<NeTExStopAssignment> getStopAssignments() {
        return stopAssignments;
    }

    public int matchedCount() {
        return matchedCount;
    }

    public int unmatchedCount() {
        return unmatchedCount;
    }

    public record NeTExScheduledStopPoint(String id, String name, String privateCode, BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record NeTExRoutePoint(String id, String stationShortCode) {
    }

    public record NeTExDestinationDisplay(String id, String frontText) {
    }

    public record NeTExStopAssignment(String id, String scheduledStopPointRef, String stopPlaceRef) {
    }
}
