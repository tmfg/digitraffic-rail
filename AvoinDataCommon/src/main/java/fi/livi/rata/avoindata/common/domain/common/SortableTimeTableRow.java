package fi.livi.rata.avoindata.common.domain.common;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow.TimeTableRowType;

/// Common shape shared by `TimeTableRow` and `GTFSTimeTableRow` (two otherwise unrelated entities) so both can
/// be ordered the same way: by {@link #getScheduledTime()}, ARRIVAL before DEPARTURE on a same-instant tie.
/// Used by `CommercialStopVisits#of`, `EtJourneyInterpreter#pairRows`, and
/// `ScheduleToTrainConverter#createTrain`, all of which assume rows are sorted this way.
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
public interface SortableTimeTableRow extends Comparable<SortableTimeTableRow> {

    @Nonnull
    ZonedDateTime getScheduledTime();

    @Nonnull
    TimeTableRowType getType();

    @Override
    default int compareTo(final SortableTimeTableRow other) {
        final int byTime = getScheduledTime().compareTo(other.getScheduledTime());
        if (byTime != 0) {
            return byTime;
        }
        return getType().compareTo(other.getType());
    }

    /// Returns a new list with `rows` sorted by scheduled time (ARRIVAL before DEPARTURE on a tie).
    static <T extends SortableTimeTableRow> List<T> orderRows(final List<T> rows) {
        final List<T> ordered = new ArrayList<>(rows);
        ordered.sort(null);
        return ordered;
    }
}

