package fi.livi.rata.avoindata.updater.service.timetable;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class ScheduleProviderService {
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AsyncRestTemplate asyncRestTemplate;

    @Autowired
    private BatchExecutionService bes;

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    private Logger log = LoggerFactory.getLogger(this.getClass());

    public List<Schedule> getAdhocSchedules(LocalDate date) throws ExecutionException, InterruptedException {
        final String url = String.format("%s/adhoc-schedule-ids?date=%s", liikeInterfaceUrl, date.toString());
        final List<Long> scheduleIds = getScheduleIds(url);

        List<Schedule> output = getSchedules(scheduleIds);

        return output;
    }

    public List<Schedule> getRegularSchedules(LocalDate date) throws ExecutionException, InterruptedException {
        final String url = String.format("%s/regular-schedule-ids?date=%s", liikeInterfaceUrl, date.toString());
        final List<Long> scheduleIds = getScheduleIds(url);

        List<Schedule> output = getSchedules(scheduleIds);

        return output;
    }

    private List<Long> getScheduleIds(final String url) {
        log.info("Fetching regular schedule ids from {}", url);
        return Lists.newArrayList(restTemplate.getForObject(url, Long[].class));
    }

    private List<Schedule> getSchedules(final List<Long> scheduleIds) throws InterruptedException, ExecutionException {
        List<Schedule> output = new ArrayList<>();

        for (final List<Long> idPartition : Lists.partition(scheduleIds, 500)) {
            final String scheduleUrl = String.format("%s/schedules?ids=%s", liikeInterfaceUrl, Joiner.on(",").join(idPartition));
            log.info("Fetching schedules {}",idPartition);
            output.addAll(Lists.newArrayList(restTemplate.getForObject(scheduleUrl, Schedule[].class)));
        }

        return output;
    }
}
