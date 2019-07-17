package fi.livi.rata.avoindata.updater.service.routeset;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class TimeTableRowByRoutesetUpdateService {
    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private RoutesetRepository routesetRepository;

    private Logger log = LoggerFactory.getLogger(this.getClass());


    public List<Train> updateByRoutesets(List<Routeset> routesets) {
        return trainLockExecutor.executeInTransactionLock(() -> {

            try {
                List<Routeset> routesetsWithValidTrain = getRoutesetsWithValidTrain(routesets);
                List<TrainId> validTrainIds = Lists.transform(routesetsWithValidTrain, s -> getTrainId(s));
                if (validTrainIds.isEmpty()) {
                    return new ArrayList<>();
                }
                Map<TrainId, Train> trainMap = Maps.uniqueIndex(trainRepository.findTrains(validTrainIds), s -> s.id);
                updateCommercialTracks(routesetsWithValidTrain, trainMap);
                List<Train> updatedTrains = Lists.newArrayList(trainMap.values());

                return updatedTrains;
            } catch (Exception e) {
                log.error("Failed to updated trains with routesets", e);
                return new ArrayList<>();
            }
        });
    }

    public List<Train> updateByTrains(List<Train> trains) {
        List<Routeset> routesetsForTrains = new ArrayList<>();
        for (Train train : trains) {
            routesetsForTrains.addAll(routesetRepository.findByTrainNumberAndDepartureDate(train.id.trainNumber.toString(), train.id.departureDate));
        }

        updateCommercialTracks(routesetsForTrains, Maps.uniqueIndex(trains, s -> s.id));
        return trains;
    }

    private void updateCommercialTracks(List<Routeset> routesets, Map<TrainId, Train> trainMap) {
        Long maxVersion = trainRepository.getMaxVersion();

        for (Routeset routeset : routesets) {
            Train train = trainMap.get(getTrainId(routeset));
            if (train == null) {
                continue;
            }

            ListMultimap<String, TimeTableRowAndItsIndex> timeTableRowsByStation = LinkedListMultimap.create();
            for (int i = 0; i < train.timeTableRows.size(); i++) {
                TimeTableRow timeTableRow = train.timeTableRows.get(i);
                timeTableRowsByStation.put(timeTableRow.station.stationShortCode, new TimeTableRowAndItsIndex(i, timeTableRow));
            }

            for (Routesection routesection : routeset.routesections) {
                if (!Strings.isNullOrEmpty(routesection.commercialTrackId)) {
                    List<TimeTableRowAndItsIndex> timeTableRowsToUpdate = timeTableRowsByStation.get(routesection.stationCode);

                    //No corresponding time-table-row found
                    if (timeTableRowsToUpdate.size() == 0) {

                    }
                    //Update a single time-table-row
                    else if (timeTableRowsToUpdate.size() == 1) {
                        updateSingleStopTimeTableRow(maxVersion, train, routesection, timeTableRowsToUpdate);
                    }
                    //Update a two consecutive time-table-rows
                    else if (timeTableRowsToUpdate.size() == 2 && Math.abs(timeTableRowsToUpdate.get(0).index - timeTableRowsToUpdate.get(1).index) == 1) {
                        updateSingleStopTimeTableRow(maxVersion, train, routesection, timeTableRowsToUpdate);
                    }
                    //Update multi-stop time-table-row. Match by scheduled time +- 30 minutes
                    else {
                        updateMultistopTimeTableRow(routeset, train, maxVersion, routesection, timeTableRowsToUpdate);
                    }
                }
            }
        }
    }

    private void updateSingleStopTimeTableRow(Long maxVersion, Train train, Routesection routesection, List<TimeTableRowAndItsIndex> timeTableRowsToUpdate) {
        for (TimeTableRowAndItsIndex timeTableRowAndItsIndex : timeTableRowsToUpdate) {
            TimeTableRow timeTableRow = timeTableRowAndItsIndex.timeTableRow;
            if (routesection.commercialTrackId.equals(timeTableRow.commercialTrack)) {
                //log.info("Not updating {} - {} because already updated {} vs {}", train, timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId);
            } else {
                setCommercialTrack(maxVersion, train, routesection, timeTableRow, timeTableRow.train);
            }
        }
    }

    private void setCommercialTrack(Long maxVersion, Train train, Routesection routesection, TimeTableRow timeTableRow, Train train2) {
        long possibleNewVersion = maxVersion + 1;
        String oldCommercialTrack = timeTableRow.commercialTrack;
        timeTableRow.commercialTrack = routesection.commercialTrackId;

        if (train.version < possibleNewVersion) {
            log.info("Updated {} - {}. Old: {}, New: {}. Version {} -> {}", train2, timeTableRow, oldCommercialTrack, timeTableRow.commercialTrack, train.version, possibleNewVersion);
            train.version = possibleNewVersion;
            timeTableRow.version = possibleNewVersion;
        } else {
            //log.info("Updated, but did not alter version {} - {}. Old: {}, New: {}. Version {}, maxVersion: {}", train2, timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId, train.version, maxVersion);
        }
    }

    private void updateMultistopTimeTableRow(Routeset routeset, Train train, Long maxVersion, Routesection routesection, List<TimeTableRowAndItsIndex> timeTableRowAndItsIndexList) {
        Collections.sort(timeTableRowAndItsIndexList, (left, right) -> {
            Long leftDiff = getDifference(routeset, left.timeTableRow);
            Long rightDiff = getDifference(routeset, right.timeTableRow);

            return leftDiff.compareTo(rightDiff);
        });


        TimeTableRow timeTableRow = timeTableRowAndItsIndexList.get(0).timeTableRow;
        if (routesection.commercialTrackId.equals(timeTableRow.commercialTrack)) {
            //log.info("Not updating {} - {} because already updated {} vs {}", train, timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId);
        } else if (getDifference(routeset, timeTableRow) > (30 * 60)) {
            //log.info("Not updating {} - {} because timestamps differ too much. {} vs {} ({})", train, timeTableRow, routeset.messageTime, timeTableRow.scheduledTime, Math.abs(Duration.between(timeTableRow.scheduledTime, routeset.messageTime).toMinutes()));
        } else {
            setCommercialTrack(maxVersion, train, routesection, timeTableRow, train);
        }
    }

    private Long getDifference(Routeset routeset, TimeTableRow ttr) {
        return Math.abs(Duration.between(ttr.scheduledTime, routeset.messageTime).toSeconds());
    }

    private List<Routeset> getRoutesetsWithValidTrain(List<Routeset> routesets) {
        Iterable<Routeset> routesetsWithValidTrain = Iterables.filter(routesets, s -> {
            try {
                long idAsLong = Long.parseLong(s.trainId.trainNumber);
                if (idAsLong > 0L && (s.trainId.departureDate != null || s.messageTime.toLocalDate() != null)) {
                    return true;
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        });

        return Lists.newArrayList(routesetsWithValidTrain);
    }

    private static class TimeTableRowAndItsIndex {

        public TimeTableRow timeTableRow;
        public int index;

        public TimeTableRowAndItsIndex(int index, TimeTableRow timeTableRow) {
            this.index = index;
            this.timeTableRow = timeTableRow;
        }
    }

    private TrainId getTrainId(Routeset routeset) {
        if (routeset.trainId.departureDate == null) {
            return new TrainId(Long.parseLong(routeset.trainId.trainNumber), routeset.messageTime.toLocalDate());
        } else {
            return new TrainId(routeset.trainId);
        }
    }
}
