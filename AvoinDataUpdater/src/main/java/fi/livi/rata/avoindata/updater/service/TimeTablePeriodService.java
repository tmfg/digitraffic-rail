package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.metadata.TimeTablePeriodRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;

@Service
public class TimeTablePeriodService {
    private final TimeTablePeriodRepository timeTablePeriodRepository;

    public TimeTablePeriodService(final TimeTablePeriodRepository timeTablePeriodRepository) {
        this.timeTablePeriodRepository = timeTablePeriodRepository;
    }

    @Transactional(readOnly = true)
    public LocalDate getLastAllocatedDate() {
        return timeTablePeriodRepository.getLastAllocatedDate();
    }

    @Transactional
    public void update(final TimeTablePeriod[] entities) {
        timeTablePeriodRepository.deleteAllInBatch();

        timeTablePeriodRepository.persist(Arrays.asList(entities));
    }
}
