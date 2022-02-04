package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.service.gtfs.realtime.FeedMessageCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class GTFSRealtimeService {
    private final TrainLocationRepository trainLocationRepository;
    private final GTFSTripRepository gtfsTripRepository;

    private static Logger log = LoggerFactory.getLogger(GTFSRealtimeService.class);

    public GTFSRealtimeService(final TrainLocationRepository trainLocationRepository, final GTFSTripRepository gtfsTripRepository) {
        this.trainLocationRepository = trainLocationRepository;
        this.gtfsTripRepository = gtfsTripRepository;
    }

    public GtfsRealtime.FeedMessage createFeedMessage() {
        final List<Long> ids = trainLocationRepository.findLatest(ZonedDateTime.now().minusMinutes(30));
        final List<TrainLocation> locations = trainLocationRepository.findAllOrderByTrainNumber(ids);

        final FeedMessageCreator creator = new FeedMessageCreator(gtfsTripRepository.findAll());

        return creator.createFeedMessage(locations);
    }


}
