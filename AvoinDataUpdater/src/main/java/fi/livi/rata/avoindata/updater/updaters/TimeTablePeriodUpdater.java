package fi.livi.rata.avoindata.updater.updaters;

import com.amazonaws.xray.AWSXRay;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import fi.livi.rata.avoindata.updater.service.TimeTablePeriodService;

@Service
public class TimeTablePeriodUpdater extends AEntityUpdater<TimeTablePeriod[]> {
    @Autowired
    private TimeTablePeriodService timeTablePeriodService;

    //Every midnight 1:01
    @Override
    @Scheduled(cron = "0 1 1 * * ?")
    protected void update() {
        AWSXRay.createSegment(this.getClass().getSimpleName(), (subsegment) -> {
            doUpdate("timetableperiods", timeTablePeriodService::update, TimeTablePeriod[].class);
        });
    }
}
