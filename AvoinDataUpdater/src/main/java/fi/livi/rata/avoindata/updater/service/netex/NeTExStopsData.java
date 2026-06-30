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

    public NeTExStopsData(
            final List<NeTExScheduledStopPoint> scheduledStopPoints,
            final List<NeTExRoutePoint> routePoints,
            final List<NeTExDestinationDisplay> destinationDisplays) {
        this.scheduledStopPoints = scheduledStopPoints;
        this.routePoints = routePoints;
        this.destinationDisplays = destinationDisplays;
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

    public record NeTExScheduledStopPoint(String id, String name, String privateCode, BigDecimal latitude,
            BigDecimal longitude) {
    }

    public record NeTExRoutePoint(String id, String stationShortCode) {
    }

    public record NeTExDestinationDisplay(String id, String frontText) {
    }
}
