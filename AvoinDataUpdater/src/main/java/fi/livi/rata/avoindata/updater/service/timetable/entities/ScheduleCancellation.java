package fi.livi.rata.avoindata.updater.service.timetable.entities;


import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ScheduleCancellation {
    public long id;

    public LocalDate startDate;
    public LocalDate endDate;

    public ScheduleCancellationType scheduleCancellationType;

    public Set<ScheduleRowPart> cancelledRows = new HashSet<>();

    public enum ScheduleCancellationType {
        /**
         * The whole day is cancelled (kokoperuminen). The whole train is cancelled on the days.
         * Source code KOPE. {@link #cancelledRows} is not meaningful.
         */
        WHOLE_DAY("KOPE"),
        /**
         * Some of the stops are cancelled (osaväliperuminen). The train runs, but the stops in
         * {@link #cancelledRows} are cancelled. Source code OVPE.
         */
        PARTIALLY("OVPE"),
        /**
         * Not a cancellation (kulkuväliperuminen). A way to tell that on some days the route is slightly
         * different, so the stops in {@link #cancelledRows} are not served. Source code KVPE.
         */
        DIFFERENT_ROUTE("KVPE");

        public final String code;

        ScheduleCancellationType(final String code) {
            this.code = code;
        }

        public static ScheduleCancellationType fromCode(final String code) {
            for (final ScheduleCancellationType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown cancellation type:" + code);
        }
    }

    @Override
    public String toString() {
        return "ScheduleCancellation{" + "id=" + id + ", startDate=" + startDate + ", endDate=" + endDate + ", scheduleCancellationType="
                + scheduleCancellationType + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduleCancellation that = (ScheduleCancellation) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
