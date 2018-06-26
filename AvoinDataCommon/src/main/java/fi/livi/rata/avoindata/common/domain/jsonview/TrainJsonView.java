package fi.livi.rata.avoindata.common.domain.jsonview;

public class TrainJsonView {
    public interface LiveTrains extends ScheduleTrains, CategoryCodeJsonView.OnlyCauseCategoryCodes {
    }

    public interface ScheduleTrains {
    }
}
