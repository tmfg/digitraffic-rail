package fi.livi.rata.avoindata.updater.service.timetable;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class ScheduleProviderService {
    @Autowired
    private RipaService ripaService;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public List<Schedule> getAdhocSchedules(final LocalDate date) throws ExecutionException, InterruptedException {
        final String path = String.format("adhoc-schedule-ids?date=%s",  date.toString());
        final List<Long> scheduleIds = getScheduleIds(path);

        final List<Schedule> output = getSchedules(scheduleIds);

        return output;
    }

    public List<Schedule> getRegularSchedules(final LocalDate date) throws ExecutionException, InterruptedException {
        final String path = String.format("regular-schedule-ids?date=%s", date.toString());
        final List<Long> scheduleIds = getScheduleIds(path);

        final List<Schedule> output = getSchedules(scheduleIds);

        return output;
    }

    private List<Long> getScheduleIds(final String path) {
        log.info("Fetching schedule ids from {}", path);
        return Arrays.asList(ripaService.getFromRipa(path, Long[].class));
    }

    private List<Schedule> getSchedules(final List<Long> scheduleIds) {
        final List<Schedule> output = new ArrayList<>();

        for (final List<Long> idPartition : Lists.partition(scheduleIds, 200)) {
            log.info("Fetching schedules {}", idPartition);
            final String path = String.format("schedules?ids=%s", Joiner.on(",").join(idPartition));

            output.addAll(Arrays.asList(ripaService.getFromRipa(path, Schedule[].class)));
        }

        return output;
    }
}
