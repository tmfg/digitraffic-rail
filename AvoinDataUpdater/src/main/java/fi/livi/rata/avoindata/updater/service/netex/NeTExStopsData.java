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
    private final int quayMatchedCount;
    private final int quayUnmatchedCount;
    private final int quayNoTrackCount;

    public NeTExStopsData(
            final List<NeTExScheduledStopPoint> scheduledStopPoints,
            final List<NeTExRoutePoint> routePoints,
            final List<NeTExDestinationDisplay> destinationDisplays) {
        this(scheduledStopPoints, routePoints, destinationDisplays, List.of(), 0, 0, 0, 0, 0);
    }

    public NeTExStopsData(
            final List<NeTExScheduledStopPoint> scheduledStopPoints,
            final List<NeTExRoutePoint> routePoints,
            final List<NeTExDestinationDisplay> destinationDisplays,
            final List<NeTExStopAssignment> stopAssignments,
            final int matchedCount,
            final int unmatchedCount) {
        this(scheduledStopPoints, routePoints, destinationDisplays, stopAssignments,
                matchedCount, unmatchedCount, 0, 0, 0);
    }

    public NeTExStopsData(
            final List<NeTExScheduledStopPoint> scheduledStopPoints,
            final List<NeTExRoutePoint> routePoints,
            final List<NeTExDestinationDisplay> destinationDisplays,
            final List<NeTExStopAssignment> stopAssignments,
            final int matchedCount,
            final int unmatchedCount,
            final int quayMatchedCount,
            final int quayUnmatchedCount,
            final int quayNoTrackCount) {
        this.scheduledStopPoints = scheduledStopPoints;
        this.routePoints = routePoints;
        this.destinationDisplays = destinationDisplays;
        this.stopAssignments = stopAssignments;
        this.matchedCount = matchedCount;
        this.unmatchedCount = unmatchedCount;
        this.quayMatchedCount = quayMatchedCount;
        this.quayUnmatchedCount = quayUnmatchedCount;
        this.quayNoTrackCount = quayNoTrackCount;
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

    public int quayMatchedCount() {
        return quayMatchedCount;
    }

    public int quayUnmatchedCount() {
        return quayUnmatchedCount;
    }

    public int quayNoTrackCount() {
        return quayNoTrackCount;
    }

    public record NeTExScheduledStopPoint(String id, String name, String privateCode, BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record NeTExRoutePoint(String id, String stationShortCode) {
    }

    public record NeTExDestinationDisplay(String id, String frontText) {
    }

    public record NeTExStopAssignment(String id, String scheduledStopPointRef, String stopPlaceRef,
            String quayRef) {
    }
}
