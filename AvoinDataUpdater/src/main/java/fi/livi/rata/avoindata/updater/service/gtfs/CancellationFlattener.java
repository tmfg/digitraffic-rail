package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.common.collect.Range;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;

@Service
public class CancellationFlattener {
    public List<ScheduleCancellation> flatten(Collection<ScheduleCancellation> scheduleCancellations) {
        ScheduleCancellation[] minAndMax = getMinMax(scheduleCancellations);

        Map<LocalDate, ScheduleCancellation> scheduleCancellationsPerDay = new HashMap<>();
        for (LocalDate date = minAndMax[0].startDate; !date.isAfter(minAndMax[1].endDate); date = date.plusDays(1)) {
            List<ScheduleCancellation> intersectingScheduleCancellations = getIntersectingScheduleCancellations(scheduleCancellations, date);

            if (intersectingScheduleCancellations.isEmpty()) {
                continue;
            }

            if (intersectingScheduleCancellations.size() > 1) {
                scheduleCancellationsPerDay.put(date, mergeScheduleCancellations(intersectingScheduleCancellations, date));
            } else {
                scheduleCancellationsPerDay.put(date, intersectingScheduleCancellations.iterator().next());
            }
        }

        for (LocalDate date = minAndMax[0].startDate; !date.isAfter(minAndMax[1].endDate); date = date.plusDays(1)) {
            ScheduleCancellation scheduleCancellation = scheduleCancellationsPerDay.get(date);
            if (scheduleCancellation != null) {
                scheduleCancellation.startDate = date;
                scheduleCancellation.endDate = date;
            }
        }

//        List<ScheduleCancellation> results = new ArrayList<>();
//        ScheduleCancellation previous = scheduleCancellationsPerDay.get(minAndMax[0].startDate);
//        for (LocalDate date = minAndMax[0].startDate; date.isBefore(minAndMax[1].endDate); date = date.plusDays(1)) {
//            ScheduleCancellation current = scheduleCancellationsPerDay.get(date);
//
//            if (current != null && previous != null && areCancellationsEqual(previous, current)) {
//                previous.endDate = date;
//            } else {
//                if (previous != null) {
//                    results.add(previous);
//                }
//                previous = current;
//            }
//        }
//        if (previous != null) {
//            results.add(previous);
//        }


        return scheduleCancellationsPerDay.values().stream().sorted((o1, o2) -> o1.startDate.compareTo(o2.startDate)).collect(Collectors.toList());
    }

    private boolean areCancellationsEqual(ScheduleCancellation today, ScheduleCancellation tomorrow) {
        List<Long> todaysIds = today.cancelledRows.stream().filter(s -> s != null).map(s -> s.id).sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        List<Long> tomorrowsIds = tomorrow.cancelledRows.stream().filter(s -> s != null).map(s -> s.id).sorted(Comparator.naturalOrder()).collect(Collectors.toList());

        return todaysIds.equals(tomorrowsIds);
    }

    private ScheduleCancellation mergeScheduleCancellations(Collection<ScheduleCancellation> cancellations, LocalDate date) {
        ScheduleCancellation firstCancellation = cancellations.iterator().next();

        ScheduleCancellation scheduleCancellation = new ScheduleCancellation();
        scheduleCancellation.endDate = date;
        scheduleCancellation.startDate = date;
        scheduleCancellation.scheduleCancellationType = firstCancellation.scheduleCancellationType;
        scheduleCancellation.id = firstCancellation.id;
        scheduleCancellation.cancelledRows = new HashSet<>();

        for (ScheduleCancellation cancellation : cancellations) {
            scheduleCancellation.cancelledRows.addAll(cancellation.cancelledRows);
        }

        return scheduleCancellation;
    }

    private List<ScheduleCancellation> getIntersectingScheduleCancellations(Collection<ScheduleCancellation> scheduleCancellations, LocalDate date) {
        List<ScheduleCancellation> results = new ArrayList<>();
        for (ScheduleCancellation scheduleCancellation : scheduleCancellations) {
            if (Range.closed(scheduleCancellation.startDate, scheduleCancellation.endDate).contains(date)) {
                results.add(scheduleCancellation);
            }
        }

        return results;
    }

    public ScheduleCancellation[] getMinMax(Collection<ScheduleCancellation> scheduleCancellations) {
        ScheduleCancellation min = null;
        ScheduleCancellation max = null;
        for (ScheduleCancellation scheduleCancellation : scheduleCancellations) {
            if (min == null || scheduleCancellation.startDate.isBefore(min.startDate)) {
                min = scheduleCancellation;
            }

            if (max == null || scheduleCancellation.endDate.isAfter(max.endDate)) {
                max = scheduleCancellation;
            }
        }

        return new ScheduleCancellation[]{min, max};
    }
}
