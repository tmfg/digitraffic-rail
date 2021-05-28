package fi.livi.rata.avoindata.updater.service.gtfs;

import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class GTFSTrainTypeService {

    public static final int OTHER_TYPE = 1700;
    public static final int COMMUTER_TYPE = 109;
    public static final int LONG_DISTANCE_TYPE = 102;

    public int getGtfsTrainType(Schedule schedule) {
        if (!schedule.trainType.commercial) {
            return OTHER_TYPE;
        } else if (schedule.trainCategory.name.equals("Commuter")) {
            return COMMUTER_TYPE;
        } else if (schedule.trainCategory.name.equals("Long-distance")) {
            return LONG_DISTANCE_TYPE;
        } else {
            return OTHER_TYPE;
        }
    }
}
