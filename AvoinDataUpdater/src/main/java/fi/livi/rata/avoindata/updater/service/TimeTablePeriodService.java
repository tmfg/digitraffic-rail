package fi.livi.rata.avoindata.updater.service;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.metadata.TimeTablePeriodRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;

@Service
public class TimeTablePeriodService {
    @Autowired
    private TimeTablePeriodRepository timeTablePeriodRepository;


    @Transactional
    public void update(final TimeTablePeriod[] entities) {
        timeTablePeriodRepository.deleteAllInBatch();

        timeTablePeriodRepository.persist(Arrays.asList(entities));
    }
}
