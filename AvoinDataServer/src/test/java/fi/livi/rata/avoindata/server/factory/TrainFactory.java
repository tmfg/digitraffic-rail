package fi.livi.rata.avoindata.server.factory;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainReadyRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;

@Component
public class TrainFactory {
    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Autowired
    private TrainReadyRepository trainReadyRepository;

    @Autowired
    private TimeTableRowFactory ttrf;

    @Autowired
    private TrainReadyFactory trainReadyFactory;

    @Transactional
    public Train createBaseTrain() {
        return createBaseTrain(new TrainId(51L, LocalDate.now()));
    }

    @Transactional
    public Train createBaseTrainWithTrainReadyMessages() {
        final Train train = createBaseTrain(new TrainId(51L, LocalDate.now()));
        train.timeTableRows.forEach(ttr -> trainReadyRepository.save(trainReadyFactory.create(ttr)));
        return train;
    }

    @Transactional
    public Train createBaseTrain(final TrainId id) {
        final int operatorUICCode = 1;
        final String operatorShortCode = "test";
        final long trainCategoryId = 1;
        final long trainTypeId = 1;
        final String commuterLineID = "Z";
        final boolean runningCurrently = true;
        final boolean cancelled = false;
        final Long version = 1L;

        final LocalDate departureDate = id.departureDate;

        Train train = new Train(id.trainNumber, departureDate, operatorUICCode, operatorShortCode, trainCategoryId, trainTypeId,
                commuterLineID, runningCurrently, cancelled, version, Train.TimetableType.REGULAR, ZonedDateTime.now());

        train = trainRepository.save(train);

        final ZonedDateTime now = ZonedDateTime.now().withYear(departureDate.getYear()).withMonth(departureDate.getMonthValue())
                .withDayOfMonth(departureDate.getDayOfMonth());
        final List<TimeTableRow> timeTableRowList = new ArrayList<>();
        timeTableRowList.add(ttrf.create(train, now.plusHours(1), now.plusHours(1).plusMinutes(1), new StationEmbeddable("HKI", 1, "FI"),
                TimeTableRow.TimeTableRowType.DEPARTURE));
        timeTableRowList.add(ttrf.create(train, now.plusHours(2), now.plusHours(2).plusMinutes(3), new StationEmbeddable("PSL", 2, "FI"),
                TimeTableRow.TimeTableRowType.ARRIVAL));
        timeTableRowList.add(ttrf.create(train, now.plusHours(3), now.plusHours(3).plusMinutes(4), new StationEmbeddable("PSL", 2, "FI"),
                TimeTableRow.TimeTableRowType.DEPARTURE));
        timeTableRowList.add(ttrf.create(train, now.plusHours(4), now.plusHours(4).plusMinutes(5), new StationEmbeddable("TPE", 3, "FI"),
                TimeTableRow.TimeTableRowType.ARRIVAL));
        timeTableRowList.add(ttrf.create(train, now.plusHours(5), now.plusHours(5).plusMinutes(1), new StationEmbeddable("TPE", 3, "FI"),
                TimeTableRow.TimeTableRowType.DEPARTURE));
        timeTableRowList.add(ttrf.create(train, now.plusHours(5), now.plusHours(5).plusMinutes(1), new StationEmbeddable("JY", 4, "FI"),
                TimeTableRow.TimeTableRowType.ARRIVAL));
        timeTableRowList.add(
                ttrf.create(train, now.plusHours(7), null, new StationEmbeddable("JY", 4, "FI"), TimeTableRow.TimeTableRowType.DEPARTURE));
        timeTableRowList.add(
                ttrf.create(train, now.plusHours(8), null, new StationEmbeddable("OL", 5, "FI"), TimeTableRow.TimeTableRowType.ARRIVAL));

        train.timeTableRows = timeTableRowRepository.saveAll(timeTableRowList);

        return train;
    }
}
