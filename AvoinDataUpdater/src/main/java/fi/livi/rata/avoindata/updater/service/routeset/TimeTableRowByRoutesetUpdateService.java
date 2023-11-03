package fi.livi.rata.avoindata.updater.service.routeset;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.common.dao.routeset.RoutesetRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.TrainLockExecutor;

@Service
public class TimeTableRowByRoutesetUpdateService {
    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private RoutesetRepository routesetRepository;

    private Logger log = LoggerFactory.getLogger(this.getClass());


    public List<Train> updateByRoutesets(final List<Routeset> routesets) {
        return trainLockExecutor.executeInTransactionLock("routesets", () -> {

            try {
                final List<Routeset> routesetsWithValidTrain = getRoutesetsWithValidTrain(routesets);
                final List<TrainId> validTrainIds = Lists.transform(routesetsWithValidTrain, s -> getTrainId(s));
                if (validTrainIds.isEmpty()) {
                    return new ArrayList<>();
                }
                final Map<TrainId, Train> trainMap = Maps.uniqueIndex(trainRepository.findTrains(validTrainIds), s -> s.id);
                updateCommercialTracks(routesetsWithValidTrain, trainMap);
                final List<Train> updatedTrains = Lists.newArrayList(trainMap.values());

                return updatedTrains;
            } catch (final Exception e) {
                log.error("Failed to updated trains with routesets", e);
                return new ArrayList<>();
            }
        });
    }

    public List<Train> updateByTrains(final List<Train> trains) {
        final List<Routeset> routesetsForTrains = new ArrayList<>();
        for (final Train train : trains) {
            routesetsForTrains.addAll(routesetRepository.findByTrainNumberAndDepartureDate(train.id.trainNumber.toString(), train.id.departureDate));
        }

        updateCommercialTracks(routesetsForTrains, Maps.uniqueIndex(trains, s -> s.id));
        return trains;
    }

    private void updateCommercialTracks(final List<Routeset> routesets, final Map<TrainId, Train> trainMap) {
        final Long maxVersion = trainRepository.getMaxVersion();

        for (final Routeset routeset : routesets) {
            final Train train = trainMap.get(getTrainId(routeset));
            if (train == null) {
                continue;
            }

            final ListMultimap<String, TimeTableRowAndItsIndex> timeTableRowsByStation = LinkedListMultimap.create();
            for (int i = 0; i < train.timeTableRows.size(); i++) {
                final TimeTableRow timeTableRow = train.timeTableRows.get(i);
                timeTableRowsByStation.put(timeTableRow.station.stationShortCode, new TimeTableRowAndItsIndex(i, timeTableRow));
            }

            for (final Routesection routesection : routeset.routesections) {
                if (!Strings.isNullOrEmpty(routesection.commercialTrackId)) {
                    final List<TimeTableRowAndItsIndex> timeTableRowsToUpdate = timeTableRowsByStation.get(routesection.stationCode);

                    //No corresponding time-table-row found
                    if (timeTableRowsToUpdate.size() == 0) {

                    }
                    //Update a single time-table-row
                    else if (timeTableRowsToUpdate.size() == 1) {
                        updateSingleStopTimeTableRow(maxVersion, train, routeset, routesection, timeTableRowsToUpdate);
                    }
                    //Update a two consecutive time-table-rows
                    else if (timeTableRowsToUpdate.size() == 2 && Math.abs(timeTableRowsToUpdate.get(0).index - timeTableRowsToUpdate.get(1).index) == 1) {
                        updateSingleStopTimeTableRow(maxVersion, train, routeset, routesection, timeTableRowsToUpdate);
                    }
                    //Update multi-stop time-table-row. Match by scheduled time +- 30 minutes
                    else {
                        updateMultistopTimeTableRow(routeset, train, maxVersion, routesection, timeTableRowsToUpdate);
                    }
                }
            }
        }
    }

    private void updateSingleStopTimeTableRow(final Long maxVersion, final Train train, final Routeset routeset, final Routesection routesection, final List<TimeTableRowAndItsIndex> timeTableRowsToUpdate) {
        for (final TimeTableRowAndItsIndex timeTableRowAndItsIndex : timeTableRowsToUpdate) {
            final TimeTableRow timeTableRow = timeTableRowAndItsIndex.timeTableRow;
            if (!isUpdatePossible(routeset, routesection, timeTableRow)) {
                //log.info("Not updating {} - {} because already updated {} vs {}", train, timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId);
            } else {
                setCommercialTrack(maxVersion, train, routesection, timeTableRow, timeTableRow.train);
            }
        }
    }

    private boolean isUpdatePossible(final Routeset routeset, final Routesection routesection, final TimeTableRow timeTableRow) {
        return !routesection.commercialTrackId.equals(timeTableRow.commercialTrack) && (timeTableRow.getCommercialTrackChanged() == null || timeTableRow.getCommercialTrackChanged().isBefore(routeset.messageTime));
    }

    private void setCommercialTrack(final Long maxVersion, final Train train, final Routesection routesection, final TimeTableRow timeTableRow, final Train train2) {
        final long possibleNewVersion = maxVersion + 1;
        final String oldCommercialTrack = timeTableRow.commercialTrack;
        timeTableRow.commercialTrack = routesection.commercialTrackId;

        if (train.version < possibleNewVersion) {
            log.info("Updated {} - {}. Old: {}, New: {}. Version {} -> {}", train2, timeTableRow, oldCommercialTrack, timeTableRow.commercialTrack, train.version, possibleNewVersion);
            train.version = possibleNewVersion;
            timeTableRow.version = possibleNewVersion;
        } else {
            //log.info("Updated, but did not alter version {} - {}. Old: {}, New: {}. Version {}, maxVersion: {}", train2, timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId, train.version, maxVersion);
        }
    }

    private void updateMultistopTimeTableRow(final Routeset routeset, final Train train, final Long maxVersion, final Routesection routesection, final List<TimeTableRowAndItsIndex> timeTableRowAndItsIndexList) {
        Collections.sort(timeTableRowAndItsIndexList, (left, right) -> {
            final Long leftDiff = getDifference(routeset, left.timeTableRow);
            final Long rightDiff = getDifference(routeset, right.timeTableRow);

            return leftDiff.compareTo(rightDiff);
        });


        final TimeTableRow timeTableRow = timeTableRowAndItsIndexList.get(0).timeTableRow;
        if (!isUpdatePossible(routeset, routesection, timeTableRow)) {
            //log.info("Not updating {} - {} because already updated {} vs {}", train, timeTableRow, timeTableRow.commercialTrack, routesection.commercialTrackId);
        } else if (getDifference(routeset, timeTableRow) > (30 * 60)) {
            //log.info("Not updating {} - {} because timestamps differ too much. {} vs {} ({})", train, timeTableRow, routeset.messageTime, timeTableRow.scheduledTime, Math.abs(Duration.between(timeTableRow.scheduledTime, routeset.messageTime).toMinutes()));
        } else {
            setCommercialTrack(maxVersion, train, routesection, timeTableRow, train);
        }
    }

    private Long getDifference(final Routeset routeset, final TimeTableRow ttr) {
        return Math.abs(Duration.between(ttr.scheduledTime, routeset.messageTime).toSeconds());
    }

    private List<Routeset> getRoutesetsWithValidTrain(final List<Routeset> routesets) {
        final Iterable<Routeset> routesetsWithValidTrain = Iterables.filter(routesets, s -> {
            try {
                final long idAsLong = Long.parseLong(s.trainId.trainNumber);
                if (idAsLong > 0L && (s.trainId.departureDate != null || s.messageTime.toLocalDate() != null)) {
                    return true;
                } else {
                    return false;
                }
            } catch (final NumberFormatException e) {
                return false;
            }
        });

        return Lists.newArrayList(routesetsWithValidTrain);
    }

    private static class TimeTableRowAndItsIndex {

        public TimeTableRow timeTableRow;
        public int index;

        public TimeTableRowAndItsIndex(final int index, final TimeTableRow timeTableRow) {
            this.index = index;
            this.timeTableRow = timeTableRow;
        }
    }

    private TrainId getTrainId(final Routeset routeset) {
        if (routeset.trainId.departureDate == null) {
            return new TrainId(Long.parseLong(routeset.trainId.trainNumber), routeset.messageTime.toLocalDate());
        } else {
            return new TrainId(routeset.trainId);
        }
    }
}
