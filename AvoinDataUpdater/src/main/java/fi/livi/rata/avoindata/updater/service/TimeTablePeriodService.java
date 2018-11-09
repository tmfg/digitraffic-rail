package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.metadata.TimeTablePeriodRepository;
import fi.livi.rata.avoindata.common.domain.timetableperiod.TimeTablePeriod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class TimeTablePeriodService {
    @Autowired
    private TimeTablePeriodRepository timeTablePeriodRepository;


    @Transactional
    public void update(final TimeTablePeriod[] entities) {
        timeTablePeriodRepository.deleteAllInBatch();

//        List<TrackRange> ranges = new ArrayList<>();
//        for (final TrackSection trackSection : trackSections) {
//            ranges.addAll(trackSection.ranges);
//        }
//        trackSectionRepository.persist(Arrays.asList(trackSections));

        timeTablePeriodRepository.persist(Arrays.asList(entities));
    }
}
