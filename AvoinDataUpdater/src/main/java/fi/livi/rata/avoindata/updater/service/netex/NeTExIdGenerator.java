package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * Generates NeTEx IDs following the {Codespace}:{ElementType}:{localId}
 * convention.
 * All IDs use the DT codespace (Digitraffic).
 */
@Service
public class NeTExIdGenerator {

    public static final String CODESPACE = "DT";

    public String authorityId(final String code) {
        return CODESPACE + ":Authority:" + code;
    }

    public String operatorId(final String shortCode) {
        return CODESPACE + ":Operator:" + shortCode;
    }

    public String lineId(final String lineIdentifier) {
        return CODESPACE + ":Line:" + lineIdentifier;
    }

    public String routeId(final String lineId, final String direction, final String hash) {
        return CODESPACE + ":Route:" + lineId + "-" + direction + "-" + hash;
    }

    public String routePointId(final String stationShortCode) {
        return CODESPACE + ":RoutePoint:" + stationShortCode;
    }

    public String scheduledStopPointId(final String stationShortCode) {
        return CODESPACE + ":ScheduledStopPoint:" + stationShortCode;
    }

    public String journeyPatternId(final String lineId, final String hash) {
        return CODESPACE + ":JourneyPattern:" + lineId + "-" + hash;
    }

    public String serviceJourneyId(final long trainNumber, final long scheduleId) {
        return CODESPACE + ":ServiceJourney:" + trainNumber + "-" + scheduleId;
    }

    public String serviceJourneyIdAdhoc(final long trainNumber, final LocalDate date) {
        return CODESPACE + ":ServiceJourney:" + trainNumber + "-" + date;
    }

    public String dayTypeId(final String hash) {
        return CODESPACE + ":DayType:" + hash;
    }

    public String operatingPeriodId(final String id) {
        return CODESPACE + ":OperatingPeriod:" + id;
    }

    public String destinationDisplayId(final String stationShortCode) {
        return CODESPACE + ":DestinationDisplay:" + stationShortCode;
    }

    public String networkId(final String code) {
        return CODESPACE + ":Network:" + code;
    }
}
