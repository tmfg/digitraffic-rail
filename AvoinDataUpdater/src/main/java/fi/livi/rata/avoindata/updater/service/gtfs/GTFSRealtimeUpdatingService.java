package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.utils.TimingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class GTFSRealtimeUpdatingService {
    private final GTFSRealtimeService gtfsRealtimeService;
    private final GTFSWritingService gtfsWritingService;

    private static Logger log = LoggerFactory.getLogger(GTFSRealtimeUpdatingService.class);

    public GTFSRealtimeUpdatingService(final GTFSRealtimeService gtfsRealtimeService,
                                       final GTFSWritingService gtfsWritingService) {
        this.gtfsRealtimeService = gtfsRealtimeService;
        this.gtfsWritingService = gtfsWritingService;
    }

//    @Scheduled(fixedRate = 1000 * 10)
    public void updateVehicleLocations() {
        TimingUtil.log(log, "updateVehicleLocations", () -> {
            final GtfsRealtime.FeedMessage message = gtfsRealtimeService.createVehiceLocationFeedMessage();

            gtfsWritingService.writeRealtimeGTFS(message, "gtfs-rt-locations");
        });
    }

//    @Scheduled(fixedRate = 1000 * 60)
    public void updateTripUpdates() {
        TimingUtil.log(log, "updateTripUpdates", () -> {
            final GtfsRealtime.FeedMessage message = gtfsRealtimeService.createTripUpdateFeedMessage();

            gtfsWritingService.writeRealtimeGTFS(message, "gtfs-rt-updates");
        });
    }

}
