package fi.livi.rata.avoindata.updater.service.gtfs;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.springframework.stereotype.Service;

@Service
public class GTFSTrainTypeService {

    public static final int OTHER_TYPE = 1700;
    public static final int COMMUTER_TYPE = 109;
    public static final int LONG_DISTANCE_TYPE = 102;

    public int getGtfsTrainType(Schedule schedule) {
        return 2;
    }
}
