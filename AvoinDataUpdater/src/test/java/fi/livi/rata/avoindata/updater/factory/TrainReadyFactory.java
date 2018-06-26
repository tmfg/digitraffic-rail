package fi.livi.rata.avoindata.updater.factory;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainReadyRepository;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.TrainReady;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashSet;

@Component
public class TrainReadyFactory {
    @Autowired
    private TrainReadyRepository trainReadyRepository;
    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Transactional
    public TrainReady create(final TimeTableRow timeTableRow) {
        final TrainReady trainReady = new TrainReady();
        trainReady.source = TrainReady.TrainReadySource.PHONE;
        trainReady.timeTableRow = timeTableRow;
        trainReady.accepted = true;
        trainReady.timestamp = ZonedDateTime.now();

        if (timeTableRow.trainReadies == null) {
            timeTableRow.trainReadies = new HashSet<>();
        }
        timeTableRow.trainReadies.add(trainReady);

        return trainReadyRepository.save(trainReady);
    }
}
