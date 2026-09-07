package fi.livi.rata.avoindata.updater.service.netex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.common.TrainId;

/**
 * Derives the NeTEx calendar (DayType, OperatingPeriod, DayTypeAssignment) from
 * the operating dates already resolved for each ServiceJourney.
 * <p>
 * The dates are taken from the resolved (train, day) map rather than re-derived
 * from the schedule weekday flags, so the calendar cannot disagree with the
 * timetable: exceptions, cancellations and superseded schedule versions have
 * already been applied by the time they get here.
 */
@Service
public class NeTExCalendarService {

    private final NeTExIdGenerator idGenerator;

    public NeTExCalendarService(final NeTExIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    /**
     * Groups journeys that run on exactly the same dates into a shared DayType.
     * A timetable repeats, so the number of distinct date sets stays far below
     * the number of journeys and does not grow with the length of the horizon.
     */
    public NeTExCalendarData createCalendarData(final Map<TrainId, String> serviceJourneyRefsByTrainDay) {
        final Map<String, TreeSet<LocalDate>> datesByJourney = new HashMap<>();
        for (final Map.Entry<TrainId, String> entry : serviceJourneyRefsByTrainDay.entrySet()) {
            datesByJourney.computeIfAbsent(entry.getValue(), key -> new TreeSet<>())
                    .add(entry.getKey().departureDate);
        }

        final Map<String, TreeSet<LocalDate>> datesByHash = new TreeMap<>();
        final Map<String, String> dayTypeRefByJourney = new LinkedHashMap<>();
        for (final Map.Entry<String, TreeSet<LocalDate>> entry : datesByJourney.entrySet()) {
            final String hash = hash(entry.getValue());
            datesByHash.putIfAbsent(hash, entry.getValue());
            dayTypeRefByJourney.put(entry.getKey(), idGenerator.dayTypeId(hash));
        }

        final List<NeTExDayType> dayTypes = new ArrayList<>();
        final Map<String, NeTExOperatingPeriod> periods = new LinkedHashMap<>();
        final List<NeTExDayTypeAssignment> assignments = new ArrayList<>();

        for (final Map.Entry<String, TreeSet<LocalDate>> entry : datesByHash.entrySet()) {
            final String hash = entry.getKey();
            final TreeSet<LocalDate> dates = entry.getValue();
            final String dayTypeId = idGenerator.dayTypeId(hash);
            final Set<DayOfWeek> weekdays = weekdaysOf(dates);
            final LocalDate from = dates.first();
            final LocalDate to = dates.last();

            final Set<LocalDate> covered = datesIn(from, to, weekdays);
            final List<LocalDate> extra = dates.stream().filter(d -> !covered.contains(d)).toList();
            final List<LocalDate> missing = covered.stream().filter(d -> !dates.contains(d)).toList();

            // A recurring pattern collapses to one period; enumerating every date is
            // only cheaper when the journey runs on scattered days.
            if (1 + extra.size() + missing.size() < dates.size()) {
                dayTypes.add(new NeTExDayType(dayTypeId, weekdays));
                final String periodId = idGenerator.operatingPeriodId(from + "_" + to);
                periods.putIfAbsent(periodId, new NeTExOperatingPeriod(periodId, from, to));

                int order = 1;
                assignments.add(NeTExDayTypeAssignment.forPeriod(
                        idGenerator.dayTypeAssignmentId(hash, order), order, dayTypeId, periodId));
                for (final LocalDate date : missing) {
                    order++;
                    assignments.add(NeTExDayTypeAssignment.forDate(
                            idGenerator.dayTypeAssignmentId(hash, order), order, dayTypeId, date, false));
                }
                for (final LocalDate date : extra) {
                    order++;
                    assignments.add(NeTExDayTypeAssignment.forDate(
                            idGenerator.dayTypeAssignmentId(hash, order), order, dayTypeId, date, true));
                }
            } else {
                dayTypes.add(new NeTExDayType(dayTypeId, EnumSet.noneOf(DayOfWeek.class)));
                int order = 0;
                for (final LocalDate date : dates) {
                    order++;
                    assignments.add(NeTExDayTypeAssignment.forDate(
                            idGenerator.dayTypeAssignmentId(hash, order), order, dayTypeId, date, true));
                }
            }
        }

        return new NeTExCalendarData(dayTypes, new ArrayList<>(periods.values()), assignments,
                dayTypeRefByJourney, datesByJourney);
    }

    private static Set<DayOfWeek> weekdaysOf(final Set<LocalDate> dates) {
        return dates.stream().map(LocalDate::getDayOfWeek)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static Set<LocalDate> datesIn(final LocalDate from, final LocalDate to, final Set<DayOfWeek> weekdays) {
        final Set<LocalDate> result = new LinkedHashSet<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek())) {
                result.add(date);
            }
        }
        return result;
    }

    /** Content-derived so a DayType keeps its id as long as its dates are unchanged. */
    private static String hash(final Set<LocalDate> dates) {
        final String canonical = dates.stream().map(LocalDate::toString).collect(Collectors.joining(","));
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record NeTExCalendarData(List<NeTExDayType> dayTypes,
            List<NeTExOperatingPeriod> operatingPeriods,
            List<NeTExDayTypeAssignment> assignments,
            Map<String, String> dayTypeRefByServiceJourney,
            Map<String, TreeSet<LocalDate>> datesByServiceJourney) {

        public static NeTExCalendarData empty() {
            return new NeTExCalendarData(List.of(), List.of(), List.of(), Map.of(), Map.of());
        }

        /** Earliest and latest date any of the given journeys runs, for validity conditions. */
        public List<LocalDate> datesOf(final List<String> serviceJourneyIds) {
            return serviceJourneyIds.stream()
                    .map(datesByServiceJourney::get)
                    .filter(dates -> dates != null)
                    .flatMap(Set::stream)
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    /** An empty weekday set means the dates are enumerated by the assignments instead. */
    public record NeTExDayType(String id, Set<DayOfWeek> daysOfWeek) {
    }

    public record NeTExOperatingPeriod(String id, LocalDate from, LocalDate to) {
    }

    public record NeTExDayTypeAssignment(String id, int order, String dayTypeRef,
            String operatingPeriodRef, LocalDate date, boolean available) {

        static NeTExDayTypeAssignment forPeriod(final String id, final int order,
                final String dayTypeRef, final String periodRef) {
            return new NeTExDayTypeAssignment(id, order, dayTypeRef, periodRef, null, true);
        }

        static NeTExDayTypeAssignment forDate(final String id, final int order,
                final String dayTypeRef, final LocalDate date, final boolean available) {
            return new NeTExDayTypeAssignment(id, order, dayTypeRef, null, date, available);
        }
    }
}
