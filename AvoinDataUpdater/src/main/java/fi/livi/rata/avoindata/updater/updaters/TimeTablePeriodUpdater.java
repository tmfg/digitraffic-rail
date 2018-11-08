package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.updater.service.TimeTablePeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
public class TimeTablePeriodUpdater extends AEntityUpdater<TimeTablePeriod[]> {
    @Autowired
    private TimeTablePeriodService timeTablePeriodService;

    @PostConstruct
    public void test() {
        update();
    }

    //Every midnight 1:01
    @Override
    @Scheduled(cron = "0 1 1 * * ?")
    protected void update() {
        doUpdate("timetableperiods", timeTablePeriodService::update, TimeTablePeriod[].class);
    }
}
