package fi.livi.rata.avoindata.updater.service.timetable;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.ScheduleFactory;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;


public class ScheduleToTrainConverterTest extends BaseTest {
    private static LocalDate extractionDate = LocalDate.of(2017, 1, 1);

    @Autowired
    private ScheduleToTrainConverter scheduleToTrainConverter;

    @Autowired
    private ScheduleFactory scheduleFactory;

    @Test
    public void simpleTrainShouldBeExracted() {
        Schedule schedule = scheduleFactory.create();

        final Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule), extractionDate).keySet().iterator().next();

        assertTrain(train);

        Assert.assertEquals(8, train.timeTableRows.size());

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
        Schedule schedule1 = scheduleFactory.create();
        Schedule schedule2 = scheduleFactory.create();
        schedule2.trainNumber = 2L;

        final Set<Train> trains = scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule1, schedule2), extractionDate).keySet();

        List<Train> orderedTrains = Lists.newArrayList(trains);
        Collections.sort(orderedTrains, (o1, o2) -> o1.id.trainNumber.compareTo(o2.id.trainNumber));


        Assert.assertEquals(2, orderedTrains.size());

        orderedTrains.get(0).id.trainNumber = 1L;
        orderedTrains.get(1).id.trainNumber = 2L;
    }

    @Test
    public void adhocTrainShouldBeExtracted() {
        Schedule schedule = scheduleFactory.create();
        schedule.timetableType = Train.TimetableType.ADHOC;
        schedule.startDate = extractionDate;
        schedule.endDate = null;

        Assert.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule), extractionDate).size());
    }

    @Test
    public void YTrainShouldBeExtracted() {
        Schedule schedule = scheduleFactory.create();
        schedule.startDate = extractionDate;
        schedule.endDate = null;
        schedule.typeCode = "Y";

        Assert.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(schedule), extractionDate).size());
    }

    @Test
    public void wholeDayCancellationShouldBeHonored() {
        Schedule cancelledSchedule = scheduleFactory.create();
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).size());
    }

    @Test
    public void scheduleExceptionsShouldBeHonored() {
        Schedule cancelledSchedule = scheduleFactory.create();
        final ScheduleException scheduleException = new ScheduleException();
        scheduleException.date = extractionDate;
        scheduleException.isRun = false;
        cancelledSchedule.scheduleExceptions.add(scheduleException);

        final Set<Train> trains = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet();
        Assert.assertEquals(0, trains.size());
    }

    @Test
    public void wholeDayCancellationShouldBeHonored2() {
        Schedule cancelledSchedule = scheduleFactory.create();
        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        cancellation.startDate = extractionDate.plusDays(1);
        cancellation.endDate = extractionDate.plusDays(2);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Assert.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).size());
    }

    @Test
    public void partialCancellationFromStartShouldBeHonored() {
        Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(0).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(1).arrival);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assert.assertEquals(true, train.timeTableRows.get(0).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(1).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(2).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(3).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(4).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(5).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(6).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(2), "TPE", 3, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(3), "JK", 4, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(4), "JK", 5, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(5), "OL", 6, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(6), "OL", 7, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(7), "RV", 8, TimeTableRow.TimeTableRowType.ARRIVAL);
    }

    @Test
    public void partialCancellationOnWrongDayShouldNotBeHonored() {
        Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate.plusDays(1);
        cancellation.endDate = extractionDate.plusDays(1);
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(0).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(1).arrival);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assert.assertEquals(false, train.timeTableRows.get(0).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(1).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(2).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(3).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(4).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(5).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(6).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(7).cancelled);

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
        Schedule cancelledSchedule = scheduleFactory.create();

        final ScheduleCancellation cancellation = new ScheduleCancellation();
        cancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        cancellation.startDate = extractionDate;
        cancellation.endDate = extractionDate;
        cancellation.cancelledRows = new HashSet<>();
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(3).arrival);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(3).departure);
        cancellation.cancelledRows.add(cancelledSchedule.scheduleRows.get(4).arrival);
        cancelledSchedule.scheduleCancellations.add(cancellation);

        Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assert.assertEquals(false, train.timeTableRows.get(0).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(1).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(2).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(3).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(4).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(5).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(6).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(0), "HKI", 1, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(1), "TPE", 2, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(2), "TPE", 3, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(3), "JK", 4, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(4), "JK", 5, TimeTableRow.TimeTableRowType.DEPARTURE);
    }

    @Test
    public void partialCancellationFromMiddleShouldBeHonored() {
        Schedule cancelledSchedule = scheduleFactory.create();

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

        Train train = scheduleToTrainConverter.extractSchedules(Arrays.asList(cancelledSchedule), extractionDate).keySet().iterator()
                .next();

        assertTrain(train);

        Assert.assertEquals(false, train.timeTableRows.get(0).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(1).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(2).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(3).cancelled);
        Assert.assertEquals(true, train.timeTableRows.get(4).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(5).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(6).cancelled);
        Assert.assertEquals(false, train.timeTableRows.get(7).cancelled);

        assertTimeTableRow(train.timeTableRows.get(0), "HKI", 1, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(5), "OL", 6, TimeTableRow.TimeTableRowType.ARRIVAL);
        assertTimeTableRow(train.timeTableRows.get(6), "OL", 7, TimeTableRow.TimeTableRowType.DEPARTURE);
        assertTimeTableRow(train.timeTableRows.get(7), "RV", 8, TimeTableRow.TimeTableRowType.ARRIVAL);
    }

    @Test
    public void trainNotInBetweenShouldNotBeExtracted() {
        Schedule okayschedule = scheduleFactory.create();
        okayschedule.startDate = extractionDate;

        Assert.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(okayschedule), extractionDate).size());

        Schedule futureSchedule = scheduleFactory.create();
        futureSchedule.startDate = extractionDate.plusDays(1);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(futureSchedule), extractionDate).size());

        Schedule historySchedule = scheduleFactory.create();
        historySchedule.startDate = extractionDate.minusDays(8);
        historySchedule.endDate = extractionDate.minusDays(1);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(historySchedule), extractionDate).size());

        Schedule okayHistorySchedule = scheduleFactory.create();
        okayHistorySchedule.startDate = extractionDate.minusDays(8);
        okayHistorySchedule.endDate = extractionDate;
        Assert.assertEquals(1, scheduleToTrainConverter.extractSchedules(Arrays.asList(okayHistorySchedule), extractionDate).size());
    }

    @Test
    public void weekdayRestrictionsShouldBeHonored() {
        Schedule mondaySchedule = scheduleFactory.create();
        mondaySchedule.runOnMonday = false;
        final LocalDate monday = extractionDate.minusDays(6);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(mondaySchedule), monday).size());

        Schedule tuesdaySchedule = scheduleFactory.create();
        tuesdaySchedule.runOnTuesday = false;
        final LocalDate tuesday = extractionDate.minusDays(5);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(tuesdaySchedule), tuesday).size());

        Schedule wednesdaySchedule = scheduleFactory.create();
        wednesdaySchedule.runOnWednesday = false;
        final LocalDate wednesday = extractionDate.minusDays(4);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(wednesdaySchedule), wednesday).size());

        Schedule thursdaySchedule = scheduleFactory.create();
        thursdaySchedule.runOnThursday = false;
        final LocalDate thursday = extractionDate.minusDays(3);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(thursdaySchedule), thursday).size());

        Schedule fridaySchedule = scheduleFactory.create();
        fridaySchedule.runOnFriday = false;
        final LocalDate friday = extractionDate.minusDays(2);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(fridaySchedule), friday).size());

        Schedule saturdaySchedule = scheduleFactory.create();
        saturdaySchedule.runOnSaturday = false;
        final LocalDate saturday = extractionDate.minusDays(1);
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(saturdaySchedule), saturday).size());

        Schedule sundaySchedule = scheduleFactory.create();
        sundaySchedule.runOnSunday = false;
        final LocalDate sunday = ScheduleToTrainConverterTest.extractionDate;
        Assert.assertEquals(0, scheduleToTrainConverter.extractSchedules(Arrays.asList(sundaySchedule), sunday).size());
    }

    private void assertTimeTableRow(final TimeTableRow timeTableRow, final String stationShortCode, final int minutes,
            final TimeTableRow.TimeTableRowType rowType) {
        Assert.assertEquals(null, timeTableRow.actualTime);
        Assert.assertEquals(rowType, timeTableRow.type);
        Assert.assertEquals(extractionDate, timeTableRow.id.departureDate);
        Assert.assertEquals(1L, timeTableRow.id.trainNumber.longValue());
        Assert.assertEquals(stationShortCode, timeTableRow.station.stationShortCode);
        Assert.assertEquals(true, timeTableRow.trainStopping);
        Assert.assertEquals(true, timeTableRow.commercialStop);
        //        Assert.assertEquals("001", timeTableRow.commercialTrack);
        Assert.assertEquals(extractionDate.atStartOfDay(ZoneId.of("Europe/Helsinki")).plusMinutes(minutes), timeTableRow.scheduledTime);
        Assert.assertEquals(null, timeTableRow.liveEstimateTime);
        Assert.assertEquals(null, timeTableRow.estimateSource);
        Assert.assertEquals(null, timeTableRow.actualTime);
        Assert.assertEquals(null, timeTableRow.differenceInMinutes);
    }

    private void assertTrain(final Train train) {
        Assert.assertEquals(train.id.departureDate, extractionDate);
        Assert.assertEquals(new Long(1L), train.id.trainNumber);

        Assert.assertEquals(false, train.cancelled);
        Assert.assertEquals("Z", train.commuterLineID);
        Assert.assertEquals(1L, train.trainCategoryId);
        Assert.assertEquals(1L, train.trainTypeId);
        Assert.assertEquals("TEST", train.operator.operatorShortCode);
        Assert.assertEquals(false, train.runningCurrently);
        Assert.assertEquals(ZonedDateTime.of(2017, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC")), train.timetableAcceptanceDate);
    }


}