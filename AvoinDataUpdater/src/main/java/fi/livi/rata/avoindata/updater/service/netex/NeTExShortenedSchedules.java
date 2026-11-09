package fi.livi.rata.avoindata.updater.service.netex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import fi.livi.rata.avoindata.common.utils.DateUtils;
import fi.livi.rata.avoindata.updater.service.timetable.CommercialStopRule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation.ScheduleCancellationType;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

/**
 * Finds the schedule that really runs on a day when some of its stops are cancelled (partial and
 * different-route cancellations), like the GTFS package does with its replacement trips.
 * The cancelled stops are left out of a copy of the schedule. The same copy is reused for every day with
 * the same cancelled stops, so the journey keeps the same id from one day to the next.
 * Create one instance per generation run.
 */
final class NeTExShortenedSchedules {

    private static final int MIN_COMMERCIAL_STOPS = 2;

    /**
     * Cache with one entry per schedule and set of removed stops, so every day with the same cancelled stops
     * reuses the same shortened copy. The key is the schedule id and a short hash of the removed stops' row ids,
     * for example {@code 12345-a1b2c3d4e5f60718}. Holds an empty value when fewer than two commercial stops remain.
     * Only avoids repeated work and keeps the copies identical; the results would be the same without it.
     * Lives as long as this instance, which is created once per generation run.
     */
    private final Map<String, Optional<Schedule>> shortenedSchedulesByKey = new HashMap<>();

    /**
     * The schedule to publish for the day: the schedule itself when no stop is cancelled, a copy without
     * the cancelled stops otherwise. Empty when fewer than two commercial stops remain, because such
     * a journey is not worth publishing.
     */
    Optional<Schedule> effectiveOn(final Schedule schedule, final LocalDate date) {
        final Set<Long> cancelledPartIds = cancelledPartIds(schedule, date, type -> type != ScheduleCancellationType.WHOLE_DAY);
        if (cancelledPartIds.isEmpty()) {
            return Optional.of(schedule);
        }

        // partitioningBy keeps the rows in journey order, which the shortened schedule and the key rely on
        final Map<Boolean, List<ScheduleRow>> rowsByCancelled = schedule.scheduleRows.stream()
                .collect(Collectors.partitioningBy(row -> isStopCancelled(row, cancelledPartIds)));
        final List<ScheduleRow> removedRows = rowsByCancelled.get(true);
        final List<ScheduleRow> keptRows = rowsByCancelled.get(false);
        if (removedRows.isEmpty()) {
            return Optional.of(schedule);
        }

        // The real train does not have the stops of a different-route cancellation, so SIRI must count visits
        // without them. Two days with the same removed stops but a different reason therefore get separate copies.
        final Set<Long> differentRoutePartIds = cancelledPartIds(schedule, date,
                type -> type == ScheduleCancellationType.DIFFERENT_ROUTE);
        final List<ScheduleRow> differentRouteRows = removedRows.stream()
                .filter(row -> isStopCancelled(row, differentRoutePartIds))
                .toList();

        final String key = removedRowsKey(removedRows, differentRouteRows);
        return shortenedSchedulesByKey.computeIfAbsent(schedule.id + "-" + key, _ -> {
            final long commercialStops = keptRows.stream().filter(CommercialStopRule::isCommercialStop).count();
            if (commercialStops < MIN_COMMERCIAL_STOPS) {
                return Optional.empty();
            }
            final Schedule copy = schedule.copyWithRows(keptRows, key);
            copy.originalRows = schedule.scheduleRows;
            copy.realtimeRows = schedule.scheduleRows.stream()
                    .filter(row -> !differentRouteRows.contains(row))
                    .toList();
            return Optional.of(copy);
        });
    }

    private static Set<Long> cancelledPartIds(final Schedule schedule, final LocalDate date,
            final Predicate<ScheduleCancellationType> typeFilter) {
        return schedule.scheduleCancellations.stream()
                .filter(cancellation -> typeFilter.test(cancellation.scheduleCancellationType)
                        && DateUtils.isInclusivelyBetween(date, cancellation.startDate, cancellation.endDate))
                .flatMap(cancellation -> cancellation.cancelledRows.stream())
                .filter(Objects::nonNull)
                .map(part -> part.id)
                .collect(Collectors.toSet());
    }

    /** Same rule as in the GTFS package: a stop with both arrival and departure stays if only one is cancelled. */
    private static boolean isStopCancelled(final ScheduleRow row, final Set<Long> cancelledPartIds) {
        final boolean arrivalCancelled = row.arrival != null && cancelledPartIds.contains(row.arrival.id);
        final boolean departureCancelled = row.departure != null && cancelledPartIds.contains(row.departure.id);
        return departureCancelled && row.arrival == null
                || arrivalCancelled && row.departure == null
                || arrivalCancelled && departureCancelled;
    }

    /**
     * Short stable key for the removed rows, and for which of them a different-route cancellation removed.
     * 64 bits of a SHA-256 digest, so different sets practically never share a key, unlike a 32-bit list hash.
     */
    private static String removedRowsKey(final List<ScheduleRow> removedRows,
            final List<ScheduleRow> differentRouteRows) {
        final String source = rowIds(removedRows) + "/" + rowIds(differentRouteRows);
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Long> rowIds(final List<ScheduleRow> rows) {
        return rows.stream()
                .map(row -> row.departure != null ? row.departure.id : row.arrival.id)
                .toList();
    }
}
