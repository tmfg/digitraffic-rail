package fi.livi.rata.avoindata.updater.factory;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;

@Component
public class ScheduleFactory {

    private long idCounter = 1L;

    public Schedule create() {
        Schedule schedule = new Schedule();

        schedule.startDate = LocalDate.MIN;
        schedule.endDate = LocalDate.MAX;
        schedule.effectiveFrom = LocalDate.MIN;
        schedule.trainNumber = 1L;

        schedule.runOnMonday = true;
        schedule.runOnTuesday = true;
        schedule.runOnTuesday = true;
        schedule.runOnThursday = true;
        schedule.runOnFriday = true;
        schedule.runOnSaturday = true;
        schedule.runOnSunday = true;

        schedule.typeCode = "K";
        schedule.changeType = "L";
        schedule.capacityId="Test__Capacity";
        schedule.version=1L;

        schedule.acceptanceDate = ZonedDateTime.now();
        schedule.commuterLineId = "Z";

        final Operator operator = new Operator();
        operator.operatorShortCode = "TEST";
        operator.operatorUICCode = 1;
        schedule.operator = operator;
        schedule.timetableType = Train.TimetableType.REGULAR;
        schedule.acceptanceDate = ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));

        final TrainCategory trainCategory = new TrainCategory();
        trainCategory.name = "test category";
        trainCategory.id = 1l;
        schedule.trainCategory = trainCategory;

        final TrainType trainType = new TrainType();
        trainType.name = "testTrainType";
        trainType.trainCategory = trainCategory;
        trainType.id = 1L;
        schedule.trainType = trainType;

        schedule.scheduleRows = new ArrayList<>();

        schedule.scheduleRows.add(createScheduleRow(schedule, 1, null, "HKI"));
        schedule.scheduleRows.add(createScheduleRow(schedule, 3, 2, "TPE"));
        schedule.scheduleRows.add(createScheduleRow(schedule, 5, 4, "JK"));
        schedule.scheduleRows.add(createScheduleRow(schedule, 7, 6, "OL"));
        schedule.scheduleRows.add(createScheduleRow(schedule, null, 8, "RV"));

        schedule.scheduleCancellations = new HashSet<>();
        schedule.scheduleExceptions = new HashSet<>();

        return schedule;
    }

    private ScheduleRow createScheduleRow(final Schedule schedule, final Integer minutesDeparture, final Integer minutesArrival,
            final String stationShortCode) {
        final ScheduleRow scheduleRow = new ScheduleRow();
        scheduleRow.schedule = schedule;
        scheduleRow.commercialTrack = "001";
        scheduleRow.station = new StationEmbeddable(stationShortCode, 1, "FI");
        if (minutesDeparture != null) {
            final ScheduleRowPart scheduleRowPartDeparture = new ScheduleRowPart();
            scheduleRowPartDeparture.timestamp = Duration.ofMinutes(minutesDeparture);
            scheduleRowPartDeparture.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
            scheduleRowPartDeparture.id = idCounter++;
            scheduleRow.departure = scheduleRowPartDeparture;
        }

        if (minutesArrival != null) {
            final ScheduleRowPart scheduleRowPartArrival = new ScheduleRowPart();
            scheduleRowPartArrival.timestamp = Duration.ofMinutes(minutesArrival);
            scheduleRowPartArrival.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
            scheduleRowPartArrival.id = idCounter++;
            scheduleRow.arrival = scheduleRowPartArrival;
        }

        return scheduleRow;
    }
}
