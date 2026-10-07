package fi.livi.rata.avoindata.common.domain.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow.TimeTableRowType;

public class SortableTimeTableRowTest {

    private record Row(ZonedDateTime scheduledTime, TimeTableRowType type) implements SortableTimeTableRow {
        @Override
        @Nonnull
        public ZonedDateTime getScheduledTime() {
            return scheduledTime;
        }

        @Override
        @Nonnull
        public TimeTableRowType getType() {
            return type;
        }
    }

    private static final ZonedDateTime T1 = ZonedDateTime.parse("2024-01-01T10:00:00Z");
    private static final ZonedDateTime T2 = ZonedDateTime.parse("2024-01-01T11:00:00Z");

    @Test
    public void compareToOrdersByScheduledTimeFirst() {
        final Row earlier = new Row(T1, TimeTableRowType.DEPARTURE);
        final Row later = new Row(T2, TimeTableRowType.ARRIVAL);

        assertThat(earlier.compareTo(later)).isLessThan(0);
        assertThat(later.compareTo(earlier)).isGreaterThan(0);
    }

    @Test
    public void compareToOrdersArrivalBeforeDepartureOnSameInstantTie() {
        final Row arrival = new Row(T1, TimeTableRowType.ARRIVAL);
        final Row departure = new Row(T1, TimeTableRowType.DEPARTURE);

        assertThat(arrival.compareTo(departure)).isLessThan(0);
        assertThat(departure.compareTo(arrival)).isGreaterThan(0);
    }

    @Test
    public void compareToIsZeroForEqualTimeAndType() {
        final Row a = new Row(T1, TimeTableRowType.ARRIVAL);
        final Row b = new Row(T1, TimeTableRowType.ARRIVAL);

        assertThat(a.compareTo(b)).isZero();
    }

    @Test
    public void orderRowsSortsByScheduledTimeThenArrivalBeforeDeparture() {
        final Row secondStationArrival = new Row(T2, TimeTableRowType.ARRIVAL);
        final Row firstStationDeparture = new Row(T1, TimeTableRowType.DEPARTURE);
        final Row firstStationArrival = new Row(T1, TimeTableRowType.ARRIVAL);

        final List<Row> unordered = List.of(secondStationArrival, firstStationDeparture, firstStationArrival);

        final List<Row> ordered = SortableTimeTableRow.orderRows(unordered);

        assertThat(ordered).containsExactly(firstStationArrival, firstStationDeparture, secondStationArrival);
    }

    @Test
    public void orderRowsDoesNotMutateTheInputList() {
        final Row secondStationArrival = new Row(T2, TimeTableRowType.ARRIVAL);
        final Row firstStationDeparture = new Row(T1, TimeTableRowType.DEPARTURE);

        final List<Row> input = List.of(secondStationArrival, firstStationDeparture);

        SortableTimeTableRow.orderRows(input);

        // List.of(...) is already immutable, but this also documents/guards the contract for any caller
        // passing a live, mutable, shared list (e.g. a JPA entity's collection field): the original order
        // must be untouched.
        assertThat(input).containsExactly(secondStationArrival, firstStationDeparture);
    }
}
