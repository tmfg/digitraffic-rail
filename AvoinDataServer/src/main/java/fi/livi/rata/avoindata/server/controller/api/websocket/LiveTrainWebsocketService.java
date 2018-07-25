package fi.livi.rata.avoindata.server.controller.api.websocket;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.server.advice.LocalizationControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.*;

@Controller
public class LiveTrainWebsocketService {
    public static final int NUMBER_OF_TRAINS_TO_ANNOUNCE = 100;
    private Long lastFetchedVersion = 0L;
    private static final String CONTEXT_PATH = "/live-trains/";

    private static final Logger log = LoggerFactory.getLogger(LiveTrainWebsocketService.class);

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private LocalizationControllerAdvice localizationControllerAdvice;

    @Autowired
    private AnnouncingService announcingService;

    @PostConstruct
    @Transactional
    private void init() {
        lastFetchedVersion = trainRepository.getMaxVersion();
    }

    @Scheduled(fixedDelay = 10000L)
    private void updateWebsockets() {
        final List<TrainId> trainIds = getChangedTrains();
        if (!trainIds.isEmpty()) {
            for (List<TrainId> trainIdPartition : Lists.partition(trainIds, NUMBER_OF_TRAINS_TO_ANNOUNCE)) {
                List<Train> trainList = trainRepository.findTrains(new HashSet<>(trainIdPartition));

                for (final Train train : trainList) {
                    localizationControllerAdvice.localizeTrain(train);
                }

                announceAll(trainList);
                announceSpecificTrains(trainList);
                announceStations(trainList);
                updateLastFetchedVersion(trainList);

                log.trace("Published {} new trains trough websocket", trainList.size());
            }
        }
    }

    private void announceStations(final List<Train> trains) {
        Map<StationEmbeddable, Set<Train>> trainsByStation = getChangedTrainsByStation(trains);


        for (final StationEmbeddable key : trainsByStation.keySet()) {
            final String trainIddestination = String.format(CONTEXT_PATH + "station/%s", key.stationShortCode);
            announcingService.announce(trainIddestination, trainsByStation.get(key));
        }
    }

    private void announceAll(final List<Train> trains) {
        announcingService.announce(CONTEXT_PATH, trains);
    }

    private void announceSpecificTrains(final List<Train> trains) {
        for (final Train train : trains) {
            TrainId trainId = train.id;
            final String trainIddestination = String.format(CONTEXT_PATH + "%s/%s", trainId.trainNumber, trainId.departureDate);
            announcingService.announce(trainIddestination, train);

            final String trainNumberDestination = String.format(CONTEXT_PATH + "%s", trainId.trainNumber);
            announcingService.announce(trainNumberDestination, train);
        }
    }

    private void updateLastFetchedVersion(final List<Train> trains) {
        for (final Train train : trains) {
            if (train.version > lastFetchedVersion) {
                lastFetchedVersion = train.version;
            }
        }
    }


    private Map<StationEmbeddable, Set<Train>> getChangedTrainsByStation(final List<Train> trains) {
        Map<StationEmbeddable, Set<Train>> trainsByStation = new HashMap<>();

        for (final Train train : trains) {
            for (final TimeTableRow timeTableRow : train.timeTableRows) {
                if (train.version > lastFetchedVersion) {
                    StationEmbeddable stationID = timeTableRow.station;
                    Set<Train> trainSet = trainsByStation.get(stationID);
                    if (trainSet == null) {
                        trainSet = new HashSet<>();
                        trainsByStation.put(stationID, trainSet);
                    }
                    trainSet.add(train);
                }
            }
        }
        return trainsByStation;
    }

    private List<TrainId> getChangedTrains() {
        return trainRepository.findByVersionGreaterThan(lastFetchedVersion, new PageRequest(0, 4000));
    }
}
