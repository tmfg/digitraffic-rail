package fi.livi.rata.avoindata.updater.service.siri.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;

/// Shared row ordering used by {@link CommercialStopVisits#of} and `EtJourneyInterpreter#pairRows`, which both
/// assume rows are sorted by scheduled time, ARRIVAL before DEPARTURE on a same-instant tie (matching
/// `ScheduleToTrainConverter#sortTimeTableRows`, used elsewhere for the same kind of ordering).
///
/// A same-station tie (a station's own ARRIVAL and DEPARTURE sharing the exact same `scheduledTime` - a
/// zero-dwell stop, e.g. a scheduled pass-by point) is common in production (a 365-day check across
/// `time_table_row` found millions of such rows) and is handled correctly here: ARRIVAL always sorts before its
/// own DEPARTURE, so the pair is never split into two bogus stops.
///
/// **Known limitation**: a cross-station tie (this station's DEPARTURE and a *different* station's ARRIVAL
/// sharing the exact same `scheduledTime` - zero scheduled transit time between adjacent stops) is not
/// reconstructed by station here and can pair rows across stations incorrectly. This is an accepted tradeoff:
/// the same 365-day production check found zero occurrences of this case, so real timetables appear to always
/// allot at least a minute of scheduled transit time between stations.
public final class TimeTableRowOrdering {
    private TimeTableRowOrdering() {
    }

    public static List<GTFSTimeTableRow> orderRows(final List<GTFSTimeTableRow> rows) {
        final List<GTFSTimeTableRow> ordered = new ArrayList<>(rows);
        ordered.sort(Comparator.<GTFSTimeTableRow, java.time.ZonedDateTime>comparing(r -> r.scheduledTime)
                .thenComparing(r -> r.type));
        return ordered;
    }
}
