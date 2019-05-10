package fi.livi.rata.avoindata.updater.service.routeset;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class TimeTableRowByRoutesetUpdateService {
    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private TrainRepository trainRepository;

    private Logger log = LoggerFactory.getLogger(this.getClass());


    public List<Train> updateTrainByRouteset(List<Routeset> routesets) {
        return trainLockExecutor.executeInLock(() -> {

            System.out.println("futuressa");
            Map<TrainId, Train> trainMap = Maps.uniqueIndex(trainRepository.findTrains(getValidTrainIds(routesets)), s -> s.id);
            for (Routeset routeset : routesets) {
                Train train = trainMap.get(new TrainId(routeset.trainId));
                ImmutableListMultimap<String, TimeTableRow> timeTableRowsByStation = Multimaps.index(train.timeTableRows, s -> s.station.stationShortCode);
                for (Routesection routesection : routeset.routesections) {
                    if (!Strings.isNullOrEmpty(routesection.commercialTrackId)) {
                        ImmutableList<TimeTableRow> timeTableRowsToUpdate = timeTableRowsByStation.get(routesection.stationCode);
                        for (TimeTableRow timeTableRow : timeTableRowsToUpdate) {
                            if (Math.abs(Duration.between(timeTableRow.scheduledTime, routeset.messageTime).toMinutes()) < 30 && !timeTableRow.commercialTrack.equals(routesection.commercialTrackId)) {
                                log.info("Updated {}. Old: {}, New: {}", timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId);
                                timeTableRow.commercialTrack = routesection.commercialTrackId;
                            } else {
                                log.info("Not updating {} because timestamps differ too much. {} vs {}", timeTableRow, routeset.messageTime, timeTableRow.scheduledTime);
                            }
                        }
                    }
                }
            }

            return Lists.newArrayList(trainMap.values());
        });
    }

    private List<TrainId> getValidTrainIds(List<Routeset> routesets) {
        Iterable<Routeset> routesetsWithValidTrain = Iterables.filter(routesets, s -> {
            try {
                long idAsLong = Long.parseLong(s.trainId.trainNumber);
                if (idAsLong > 0L) {
                    return true;
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        });

        return Lists.transform(Lists.newArrayList(routesetsWithValidTrain), s -> new TrainId(s.trainId));
    }
}
