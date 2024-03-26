package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.ScheduleFactory;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;


public class ScheduleToTrainConverterTest extends BaseTest {
    private static LocalDate extractionDate = LocalDate.of(2017, 1, 1);

    @Autowired
    private ScheduleToTrainConverter scheduleToTrainConverter;

    @Autowired
    private ScheduleFactory scheduleFactory;

    @Test
    public void simpleTrainShouldBeExracted() {
        final Schedule schedule = scheduleFactory.create();

        final Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule), extractionDate).keySet().iterator().next();

        assertTrain(train);

        Assertions.assertEquals(8, train.timeTableRows.size());

        assertTimeTableRow(train.timeTableRows.get(0), "HKI", 1, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(1), "TPE", 2, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(2), "TPE", 3, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(3), "JK", 4, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(4), "JK", 5, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(5), "OL", 6, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(6), "OL", 7, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(7), "RV", 8, TimeTableRow.TimeTableRowType.ARRIVAL);
    }

    @Test
    public void multipleTrainsShouldBeExracted() {
        final Schedule schedule1 = scheduleFactory.create();
        final Schedule schedule2 = scheduleFactory.create();
        schedule2.trainNumber = 2L;

        final Set<Train> trains = scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule1, schedule2), extractionDate).keySet();

        final List<Train> orderedTrains = Lists.newArrayList(trains);
        Collections.sort(orderedTrains, (o1, o2) -> o1.id.trainNumber.compareTo(o2.id.trainNumber));


        Assertions.assertEquals(2, orderedTrains.size());

        orderedTrains.get(0).id.trainNumber = 1L;
        orderedTrains.get(1).id.trainNumber = 2L;
    }

    @Test
    public void adhocTrainShouldBeExtracted() {
        final Schedule schedule = scheduleFactory.create();
        schedule.timetableType = Train.TimetableType.ADHOC;
        schedule.startDate = extractionDate;
        schedule.endDate = null;

        Assertions.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule), extractionDate).size());
    }

    @Test
    public void YTrainShouldBeExtracted() {
        final Schedule schedule = scheduleFactory.create();
        schedule.startDate = extractionDate;
        schedule.endDate = null;
        schedule.typeCode = "Y";

        Assertions.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule), extractionDate).size());
    }

    @Test
    public void wholeDayCancellationShouldBeHonored() {
        final Schedule cancelledSchedule = scheduleFactory.create();
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).size());
    }

    @Test
    public void scheduleExceptionsShouldBeHonored() {
        final Schedule cancelledSchedule = scheduleFactory.create();
        final ScheduleException scheduleException = new ScheduleException();
        scheduleException.date = extractionDate;
        scheduleException.isRun = false;
        cancelledSchedule.scheduleExceptions.add(scheduleException);

        final Set<Train> trains = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet();
        Assertions.assertEquals(0, trains.size());
    }

    @Test
    public void wholeDayCancellationShouldBeHonored2() {
        final Schedule cancelledSchedule = scheduleFactory.create();
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        cancellation.startDate = extractionDate.plusDays(1);
        cancellation.endDate = extractionDate.plusDays(2);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Assertions.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).size());
    }

    @Test
    public void partialCancellationFromStartShouldBeHonored() {
        final Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(0).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(1).arrival);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        final Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assertions.assertEquals(true, train.timeTableRows.get(0).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(1).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(2).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(3).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(4).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(5).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(6).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(2), "TPE", 3, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(3), "JK", 4, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(4), "JK", 5, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(5), "OL", 6, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(6), "OL", 7, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(7), "RV", 8, TimeTableRow.TimeTableRowType.ARRIVAL);
    }

    @Test
    public void partialCancellationOnWrongDayShouldNotBeHonored() {
        final Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate.plusDays(1);
        cancellation.endDate = extractionDate.plusDays(1);
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(0).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(1).arrival);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        final Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assertions.assertEquals(false, train.timeTableRows.get(0).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(1).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(2).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(3).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(4).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(5).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(6).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(0), "HKI", 1, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(1), "TPE", 2, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(2), "TPE", 3, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(3), "JK", 4, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(4), "JK", 5, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(5), "OL", 6, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(6), "OL", 7, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(7), "RV", 8, TimeTableRow.TimeTableRowType.ARRIVAL);
    }

    @Test
    public void partialCancellationFromEndShouldBeHonored() {
        final Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(3).arrival);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(3).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(4).arrival);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        final Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assertions.assertEquals(false, train.timeTableRows.get(0).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(1).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(2).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(3).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(4).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(5).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(6).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(0), "HKI", 1, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(1), "TPE", 2, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(2), "TPE", 3, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(3), "JK", 4, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(4), "JK", 5, TimeTableRow.TimeTableRowType.DEPARTURE);
    }

    @Test
    public void partialCancellationFromMiddleShouldBeHonored() {
        final Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(1).arrival);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(1).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(2).arrival);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(2).departure);

        cancelledSchedule.scheduleCancellations.add(cancellation);

        final Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assertions.assertEquals(false, train.timeTableRows.get(0).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(1).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(2).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(3).cancelled);
        Assertions.assertEquals(true, train.timeTableRows.get(4).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(5).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(6).cancelled);
        Assertions.assertEquals(false, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(0), "HKI", 1, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(5), "OL", 6, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(6), "OL", 7, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(7), "RV", 8, TimeTableRow.TimeTableRowType.ARRIVAL);
    }

    @Test
    public void trainNotInBetweenShouldNotBeExtracted() {
        final Schedule okayschedule = scheduleFactory.create();
        okayschedule.startDate = extractionDate;

        Assertions.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(okayschedule), extractionDate).size());

        final Schedule futureSchedule = scheduleFactory.create();
        futureSchedule.startDate = extractionDate.plusDays(1);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(futureSchedule), extractionDate).size());

        final Schedule historySchedule = scheduleFactory.create();
        historySchedule.startDate = extractionDate.minusDays(8);
        historySchedule.endDate = extractionDate.minusDays(1);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(historySchedule), extractionDate).size());

        final Schedule okayHistorySchedule = scheduleFactory.create();
        okayHistorySchedule.startDate = extractionDate.minusDays(8);
        okayHistorySchedule.endDate = extractionDate;
        Assertions.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(okayHistorySchedule), extractionDate).size());
    }

    @Test
    public void weekdayRestrictionsShouldBeHonored() {
        final Schedule mondaySchedule = scheduleFactory.create();
        mondaySchedule.runOnMonday = false;
        final LocalDate monday = extractionDate.minusDays(6);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(mondaySchedule), monday).size());

        final Schedule tuesdaySchedule = scheduleFactory.create();
        tuesdaySchedule.runOnTuesday = false;
        final LocalDate tuesday = extractionDate.minusDays(5);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(tuesdaySchedule), tuesday).size());

        final Schedule wednesdaySchedule = scheduleFactory.create();
        wednesdaySchedule.runOnWednesday = false;
        final LocalDate wednesday = extractionDate.minusDays(4);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(wednesdaySchedule), wednesday).size());

        final Schedule thursdaySchedule = scheduleFactory.create();
        thursdaySchedule.runOnThursday = false;
        final LocalDate thursday = extractionDate.minusDays(3);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(thursdaySchedule), thursday).size());

        final Schedule fridaySchedule = scheduleFactory.create();
        fridaySchedule.runOnFriday = false;
        final LocalDate friday = extractionDate.minusDays(2);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(fridaySchedule), friday).size());

        final Schedule saturdaySchedule = scheduleFactory.create();
        saturdaySchedule.runOnSaturday = false;
        final LocalDate saturday = extractionDate.minusDays(1);
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(saturdaySchedule), saturday).size());

        final Schedule sundaySchedule = scheduleFactory.create();
        sundaySchedule.runOnSunday = false;
        final LocalDate sunday = ScheduleToTrainConverterTest.extractionDate;
        Assertions.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(sundaySchedule), sunday).size());
    }

    private void assertTimeTableRow(final TimeTableRow timeTableRow, final String stationShortCode, final int minutes,
            final TimeTableRow.TimeTableRowType rowType) {
        Assertions.assertEquals(null, timeTableRow.actualTime);
        Assertions.assertEquals(rowType, timeTableRow.type);
        Assertions.assertEquals(extractionDate, timeTableRow.id.departureDate);
        Assertions.assertEquals(1L, timeTableRow.id.trainNumber.longValue());
        Assertions.assertEquals(stationShortCode, timeTableRow.station.stationShortCode);
        Assertions.assertEquals(true, timeTableRow.trainStopping);
        Assertions.assertEquals(true, timeTableRow.commercialStop);
        //        Assertions.assertEquals("001", timeTableRow.commercialTrack);
        Assertions.assertEquals(extractionDate.atStartOfDay(ZoneId.of("Europe/Helsinki")).plusMinutes(minutes), timeTableRow.scheduledTime);
        Assertions.assertEquals(null, timeTableRow.liveEstimateTime);
        Assertions.assertEquals(null, timeTableRow.estimateSource);
        Assertions.assertEquals(null, timeTableRow.actualTime);
        Assertions.assertEquals(null, timeTableRow.differenceInMinutes);
    }

    private void assertTrain(final Train train) {
        Assertions.assertEquals(train.id.departureDate, extractionDate);
        Assertions.assertEquals(1L, train.id.trainNumber);

        Assertions.assertEquals(false, train.cancelled);
        Assertions.assertEquals("Z", train.commuterLineID);
        Assertions.assertEquals(1L, train.trainCategoryId);
        Assertions.assertEquals(1L, train.trainTypeId);
        Assertions.assertEquals("TEST", train.operator.operatorShortCode);
        Assertions.assertEquals(false, train.runningCurrently);
        Assertions.assertEquals(ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")), train.timetableAcceptanceDate);
    }


}