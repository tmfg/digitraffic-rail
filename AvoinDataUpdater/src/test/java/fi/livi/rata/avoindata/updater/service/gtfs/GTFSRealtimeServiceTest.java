package fi.livi.rata.avoindata.updater.service.gtfs;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GTFSRealtimeServiceTest extends BaseTest {
    @Autowired
    private GTFSRealtimeService gtfsRealtimeService;

    @Test
    public void testCreate() {
        final GtfsRealtime.FeedMessage fm = gtfsRealtimeService.createFeedMessage();

        System.out.println(fm);
    }
}
