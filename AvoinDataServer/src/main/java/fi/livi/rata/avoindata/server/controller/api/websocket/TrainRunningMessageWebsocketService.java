package fi.livi.rata.avoindata.server.controller.api.websocket;

import com.amazonaws.xray.AWSXRay;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;

import javax.annotation.PostConstruct;
import java.util.List;

@Controller
public class TrainRunningMessageWebsocketService {
    private Long lastFetchedVersion = 0L;
    private static final String CONTEXT_PATH = "/train-tracking/";
    private static final Logger log = LoggerFactory.getLogger(TrainRunningMessageWebsocketService.class);

    @Autowired
    private TrainRunningMessageRepository trainRunningMessageRepository;

    @Autowired
    private AnnouncingService announcingService;

    @PostConstruct
    private void init() {
        AWSXRay.createSegment(this.getClass().getSimpleName(), (subsegment) -> {
            lastFetchedVersion = trainRunningMessageRepository.getMaxVersion();
        });
    }

    @Scheduled(fixedDelay = 2000L)
    private void updateWebsockets() {
        AWSXRay.createSegment(this.getClass().getSimpleName(), (subsegment) -> {

            final List<TrainRunningMessage> trainRunningMessages = getChangedTrains();
            if (!trainRunningMessages.isEmpty()) {
                announceAll(trainRunningMessages);
                announceSpecificTrains(trainRunningMessages);
                updateLastFetchedVersion(trainRunningMessages);
            }
        });
    }

    private List<TrainRunningMessage> getChangedTrains() {
        return trainRunningMessageRepository.findByVersionGreaterThan(lastFetchedVersion, new PageRequest(0, 5000));
    }

    private void announceAll(final List<TrainRunningMessage> trainRunningMessages) {
        announcingService.announce(CONTEXT_PATH, trainRunningMessages);
        announcingService.announce("/", trainRunningMessages);
    }

    private void announceSpecificTrains(final List<TrainRunningMessage> trainRunningMessages) {
        final ImmutableListMultimap<StringTrainId, TrainRunningMessage> multimap = Multimaps.index(trainRunningMessages, s -> s.trainId);
        for (final StringTrainId trainId : multimap.keySet()) {
            final ImmutableList<TrainRunningMessage> trainsTrainRunningMessages = multimap.get(trainId);

            if (trainId.departureDate != null) {
                final String trainIddestination = String.format("%s/%s", trainId.trainNumber, trainId.departureDate);
                announcingService.announce(CONTEXT_PATH + trainIddestination, trainsTrainRunningMessages);
                announcingService.announce(trainIddestination, trainsTrainRunningMessages);
            }

            final String trainNumberDestination = String.format("%s", trainId.trainNumber);
            announcingService.announce(CONTEXT_PATH + trainNumberDestination, trainsTrainRunningMessages);
            announcingService.announce(trainNumberDestination, trainsTrainRunningMessages);
        }
    }

    private void updateLastFetchedVersion(final List<TrainRunningMessage> trainRunningMessages) {
        for (final TrainRunningMessage trainRunningMessage : trainRunningMessages) {
            if (trainRunningMessage.version > lastFetchedVersion) {
                lastFetchedVersion = trainRunningMessage.version;
            }
        }
    }
}
