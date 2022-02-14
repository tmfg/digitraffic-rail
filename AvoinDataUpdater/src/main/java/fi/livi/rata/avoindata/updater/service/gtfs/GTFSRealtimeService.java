package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTrainRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class GTFSRealtimeService {
    private final TrainRepository trainRepository;
    private final TrainLocationRepository trainLocationRepository;
    private final GTFSTripRepository gtfsTripRepository;
    private final GTFSWritingService gtfsWritingService;
    private final GTFSTrainRepository gtfsTrainRepository;

    private static Logger log = LoggerFactory.getLogger(GTFSRealtimeService.class);

    public GTFSRealtimeService(final TrainRepository trainRepository,
                               final TrainLocationRepository trainLocationRepository,
                               final GTFSTripRepository gtfsTripRepository,
                               final GTFSWritingService gtfsWritingService,
                               final GTFSTrainRepository gtfsTrainRepository) {
        this.trainRepository = trainRepository;
        this.trainLocationRepository = trainLocationRepository;
        this.gtfsTripRepository = gtfsTripRepository;
        this.gtfsWritingService = gtfsWritingService;
        this.gtfsTrainRepository = gtfsTrainRepository;
    }

    @Transactional(readOnly = true)
    public GtfsRealtime.FeedMessage createVehiceLocationFeedMessage() {
        final List<Long> ids = trainLocationRepository.findLatest(ZonedDateTime.now().minusMinutes(30));
        final List<TrainLocation> locations = trainLocationRepository.findAllOrderByTrainNumber(ids);

        final FeedMessageCreator creator = new FeedMessageCreator(gtfsTripRepository.findAll());

        return creator.createVehicleLocationFeedMessage(locations);
    }

    @Transactional(readOnly = true)
    public GtfsRealtime.FeedMessage createTripUpdateFeedMessage() {
        final List<GTFSTrain> trains = getTrainsForTripUpdate();

        final FeedMessageCreator creator = new FeedMessageCreator(gtfsTripRepository.findAll());

//        log.info("train count " + trains.size());
        
        return creator.createTripUpdateFeedMessage(trains);
    }

    private List<GTFSTrain> getTrainsForTripUpdate() {
        final Long maxVersion = gtfsTripRepository.getMaxVersion();
        final List<GTFSTrain> trains = new ArrayList<>();

//        log.info("gtfs max version " + maxVersion);
//        log.info("train max version " + trainRepository.getMaxVersion());

        if(maxVersion == null) {
            log.error("null version from gtfs-trips!");
        } else {
            TimingUtil.log(log, "getTrainsForTripUpdate", () -> {
                trains.addAll(gtfsTrainRepository.findByVersionGreaterThan(maxVersion));
            });
        }

        return trains;
    }
}
