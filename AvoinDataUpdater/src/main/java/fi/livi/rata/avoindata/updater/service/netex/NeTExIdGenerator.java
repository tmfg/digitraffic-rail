package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * Generates NeTEx IDs following the {Codespace}:{ElementType}:{localId}
 * convention.
 * All IDs use the FTR codespace (Fintraffic).
 */
@Service
public class NeTExIdGenerator {

    public static final String CODESPACE = "FTR";

    public String authorityId(final String code) {
        return CODESPACE + ":Authority:" + code;
    }

    public String operatorId(final String shortCode) {
        return CODESPACE + ":Operator:" + shortCode;
    }

    public String lineId(final String lineIdentifier) {
        return CODESPACE + ":Line:" + lineIdentifier;
    }

    public String routeId(final String lineId, final String hash) {
        return CODESPACE + ":Route:" + lineId + "-" + hash;
    }

    public String routePointId(final String stationShortCode) {
        return CODESPACE + ":RoutePoint:" + stationShortCode;
    }

    public String scheduledStopPointId(final String stationShortCode) {
        return CODESPACE + ":ScheduledStopPoint:" + stationShortCode;
    }

    /**
     * Track-qualified SSP ID: DT:ScheduledStopPoint:{shortCode}-{track}.
     * Falls back to station-level when track is null or blank.
     */
    public String scheduledStopPointId(final String stationShortCode, final String track) {
        if (track == null || track.isBlank()) {
            return scheduledStopPointId(stationShortCode);
        }
        return CODESPACE + ":ScheduledStopPoint:" + stationShortCode + "-" + track;
    }

    public String journeyPatternId(final String lineId, final String hash) {
        return CODESPACE + ":JourneyPattern:" + lineId + "-" + hash;
    }

    public String stopPointInJourneyPatternId(final String journeyPatternId, final int order) {
        return journeyPatternId + "-" + order;
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

    public String operatingDayId(final LocalDate date) {
        return CODESPACE + ":OperatingDay:" + date;
    }

    /**
     * Canonical dated production journey (timetables): train number + operating
     * day.
     */
    public String datedServiceJourneyId(final long trainNumber, final LocalDate date) {
        return CODESPACE + ":DatedServiceJourney:" + trainNumber + "-" + date;
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

    public String vehicleTypeId(final String typeName) {
        return CODESPACE + ":VehicleType:" + typeName;
    }

    public String datedServiceJourneyId(final long trainNumber, final LocalDate date, final String beginStation) {
        final String suffix = beginStation != null ? "-" + beginStation : "";
        return CODESPACE + ":DatedServiceJourney:" + trainNumber + "-" + date + suffix;
    }

    
    public String passengerStopAssignmentId(final String stationShortCode) {
        return CODESPACE + ":PassengerStopAssignment:" + stationShortCode;
    }

    /**
     * Track-qualified PassengerStopAssignment ID.
     * Falls back to station-level when track is null or blank.
     */
    public String passengerStopAssignmentId(final String stationShortCode, final String track) {
        if (track == null || track.isBlank()) {
            return passengerStopAssignmentId(stationShortCode);
        }
        return CODESPACE + ":PassengerStopAssignment:" + stationShortCode + "-" + track;
    }
}
