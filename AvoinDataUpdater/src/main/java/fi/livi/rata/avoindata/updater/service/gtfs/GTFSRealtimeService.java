package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GTFSRealtimeService {
    private final TrainLocationRepository trainLocationRepository;
    private final GTFSTrainRepository gtfsTrainRepository;
    private final GTFSTripRepository gtfsTripRepository;
    private final FeedMessageService feedMessageService;

    private static Logger log = LoggerFactory.getLogger(GTFSRealtimeService.class);

    public GTFSRealtimeService(final TrainLocationRepository trainLocationRepository,
                               final GTFSTripRepository gtfsTripRepository,
                               final GTFSTrainRepository gtfsTrainRepository,
                               final FeedMessageService feedMessageService) {
        this.trainLocationRepository = trainLocationRepository;
        this.gtfsTripRepository = gtfsTripRepository;
        this.gtfsTrainRepository = gtfsTrainRepository;
        this.feedMessageService = feedMessageService;
    }

    @Transactional(readOnly = true)
    public GtfsRealtime.FeedMessage createVehiceLocationFeedMessage() {
        final List<Long> ids = trainLocationRepository.findLatest(ZonedDateTime.now().minusMinutes(30));
        final List<TrainLocation> locations = trainLocationRepository.findAllOrderByTrainNumber(ids);

        return feedMessageService.createVehicleLocationFeedMessage(locations);
    }

    @Transactional(readOnly = true)
    public GtfsRealtime.FeedMessage createTripUpdateFeedMessage() {
        final List<GTFSTrain> trains = getTrainsForTripUpdate();

        return feedMessageService.createTripUpdateFeedMessage(trains);
    }

    private List<GTFSTrain> getTrainsForTripUpdate() {
        final Long maxVersion = gtfsTripRepository.getMaxVersion();
        final List<GTFSTrain> trains = new ArrayList<>();

        if(maxVersion == null) {
            log.error("null version from gtfs-trips!");
        } else {
            TimingUtil.log(log, "getTrainsForTripUpdate", () -> {
                log.info("Getting trains since version {}", maxVersion);
                final List<GTFSTrain> gtfsTrains = gtfsTrainRepository.findByVersionGreaterThan(maxVersion);
                log.info("Found {} GtfsTrains", gtfsTrains.size());

                trains.addAll(gtfsTrains);
            });
        }

        return trains;
    }
}
