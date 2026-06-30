package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

/**
 * Generates NeTEx IDs following the {Codespace}:{ElementType}:{localId} convention.
 * All IDs use the DT codespace (Digitraffic).
 */
public class NeTExIdGenerator {

    public static final String CODESPACE = "DT";

    public String authorityId(final String code) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String operatorId(final String shortCode) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String lineId(final String lineIdentifier) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String routeId(final String lineId, final String direction, final String hash) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String routePointId(final String stationShortCode) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String scheduledStopPointId(final String stationShortCode) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String journeyPatternId(final String lineId, final String hash) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String serviceJourneyId(final long trainNumber, final long scheduleId) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String serviceJourneyIdAdhoc(final long trainNumber, final LocalDate date) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String dayTypeId(final String hash) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String operatingPeriodId(final String id) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String destinationDisplayId(final String stationShortCode) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String networkId(final String code) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
