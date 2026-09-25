package fi.livi.rata.avoindata.updater.service.timetable;

import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;

/**
 * The passenger-commercial-stop selection rule, shared by the NeTEx static timetable and the SIRI-ET
 * real-time feed so both pick the exact same stops.
 */
public final class CommercialStopRule {

    /** One side of a stop: absent (a journey endpoint), or present and either commercial or not. */
    public enum Leg {
        ABSENT,
        COMMERCIAL,
        NON_COMMERCIAL
    }

    private CommercialStopRule() {
    }

    /**
     * A stop is commercial when it is a journey endpoint — the origin has no arrival leg, the terminus no
     * departure leg — or when either present leg is a commercial stop.
     */
    public static boolean isCommercialStop(final Leg arrival, final Leg departure) {
        if (arrival == Leg.ABSENT || departure == Leg.ABSENT) {
            return true;
        }
        return arrival == Leg.COMMERCIAL || departure == Leg.COMMERCIAL;
    }

    public static boolean isCommercialStop(final ScheduleRow row) {
        return isCommercialStop(leg(row.arrival), leg(row.departure));
    }

    private static Leg leg(final ScheduleRowPart part) {
        if (part == null) {
            return Leg.ABSENT;
        }
        return part.stopType == ScheduleRow.ScheduleRowStopType.COMMERCIAL ? Leg.COMMERCIAL : Leg.NON_COMMERCIAL;
    }
}
