package fi.livi.rata.avoindata.updater.service.timetable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fi.livi.rata.avoindata.updater.config.HttpInputObjectMapper;
import org.apache.commons.collections4.ListUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.ScheduleFactory;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleException;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;

public class SingleDayScheduleExtractServiceTest extends BaseTest {
    @Autowired
    private ScheduleFactory scheduleFactory;

    @Autowired
    private SingleDayScheduleExtractService singleDayScheduleExtractService;

    @Autowired
    private HttpInputObjectMapper httpInputObjectMapper;

    final LocalDate extractDate = LocalDate.of(2017, 1, 1);

    private List<Train> extract(final List<Schedule> adhoc, final List<Schedule> regular, final LocalDate date) {
        final var trains = singleDayScheduleExtractService.extractTrains(adhoc, regular, date);
        final var extractedTrains = singleDayScheduleExtractService.extract(trains.getRight(), date, true);

        // need cancelled trains for testing
        return ListUtils.union(ListUtils.union(extractedTrains.added(), extractedTrains.updated()), extractedTrains.cancelled());
    }

    @Test
    @Transactional
    public void doubleCapacityIdShouldBeFine() {
        final Schedule schedule = scheduleFactory.create();
        schedule.capacityId = "1A";
        schedule.id = 2L;

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.capacityId = "2A";
        schedule2.id = 3L;
        schedule2.changeType = "P";

        final List<Train> trains = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2),
                LocalDate.of(2017, 12, 26));
        Assertions.assertEquals(1, trains.size());
        Assertions.assertFalse(trains.getFirst().cancelled);
    }

    @Test
    @Transactional
    public void doubleCapacityIdShouldBeFine2() {
        final Schedule schedule = scheduleFactory.create();
        schedule.capacityId = "1A";
        schedule.id = 3L;

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.capacityId = "2A";
        schedule2.id = 2L;
        schedule2.changeType = "P";

        final List<Train> trains = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2),
                LocalDate.of(2017, 12, 26));
        Assertions.assertEquals(1, trains.size());
        Assertions.assertFalse(trains.getFirst().cancelled);
    }

    @Test
    @Transactional
    public void exceptionDaysShouldDominate() {
        final Schedule schedule = scheduleFactory.create();
        schedule.runOnMonday = true;
        schedule.runOnTuesday = false;
        schedule.runOnTuesday = false;
        schedule.runOnThursday = false;
        schedule.runOnFriday = false;
        schedule.runOnSaturday = false;
        schedule.runOnSunday = true;

        final ScheduleException scheduleException = new ScheduleException();
        scheduleException.isRun = true;
        //Tuesday
        scheduleException.date = LocalDate.of(2017, 12, 26);
        scheduleException.id = 1L;
        schedule.scheduleExceptions.add(scheduleException);

        Assertions.assertEquals(1,
                extract(Collections.emptyList(), Lists.newArrayList(schedule), LocalDate.of(2017, 12, 26)).size());

        final ScheduleException scheduleException2 = new ScheduleException();
        scheduleException2.isRun = false;
        //Monday
        scheduleException2.date = LocalDate.of(2017, 12, 25);
        scheduleException2.id = 2L;
        schedule.scheduleExceptions.add(scheduleException2);

        Assertions.assertEquals(0,
                extract(Collections.emptyList(), Lists.newArrayList(schedule), LocalDate.of(2017, 12, 25)).size());
    }

    @Test
    @Transactional
    @Rollback
    public void timesShouldBeOkayDuringSpring() {
        final Schedule schedule = scheduleFactory.create();

        schedule.scheduleRows.getFirst().departure.timestamp = Duration.ofMinutes(30);
        schedule.scheduleRows.get(1).arrival.timestamp = Duration.ofMinutes(30 * 2);
        schedule.scheduleRows.get(1).departure.timestamp = Duration.ofMinutes(30 * 3);
        schedule.scheduleRows.get(2).arrival.timestamp = Duration.ofMinutes(30 * 4);
        schedule.scheduleRows.get(2).departure.timestamp = Duration.ofMinutes(30 * 5);
        schedule.scheduleRows.get(3).arrival.timestamp = Duration.ofMinutes(30 * 6);
        schedule.scheduleRows.get(3).departure.timestamp = Duration.ofMinutes(30 * 7);
        schedule.scheduleRows.get(4).arrival.timestamp = Duration.ofMinutes(30 * 8);

        final LocalDate train1DepartureDate = LocalDate.of(2018, 3, 24);
        final Train train1 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train1DepartureDate).getFirst();
        assertTimes(train1.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train1DepartureDate, 0, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 1, 0, 2),//
                createTimeWithOffset(train1DepartureDate, 1, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 2, 0, 2),//
                createTimeWithOffset(train1DepartureDate, 2, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 3, 0, 2),//
                createTimeWithOffset(train1DepartureDate, 3, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 4, 0, 2),//
        });

        final LocalDate train2DepartureDate = LocalDate.of(2018, 3, 25);
        final Train train2 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train2DepartureDate).getFirst();
        assertTimes(train2.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train2DepartureDate, 0, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 1, 0, 2),//
                createTimeWithOffset(train2DepartureDate, 1, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 2, 0, 2),//
                createTimeWithOffset(train2DepartureDate, 2, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 4, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 4, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 5, 0, 3),//
        });


        final LocalDate train3DepartureDate = LocalDate.of(2018, 3, 26);
        final Train train3 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train3DepartureDate).getFirst();
        assertTimes(train3.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train3DepartureDate, 0, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 1, 0, 3),//
                createTimeWithOffset(train3DepartureDate, 1, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 2, 0, 3),//
                createTimeWithOffset(train3DepartureDate, 2, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 3, 0, 3), //
                createTimeWithOffset(train3DepartureDate, 3, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 4, 0, 3), //
        });
    }

    @Test
    @Transactional
    public void timesShouldBeOkayDuringSpring2() {
        final Schedule schedule = scheduleFactory.create();

        schedule.scheduleRows.getFirst().departure.timestamp = Duration.ofMinutes(480 + 30);
        schedule.scheduleRows.get(1).arrival.timestamp = Duration.ofMinutes(480 + 30 * 2);
        schedule.scheduleRows.get(1).departure.timestamp = Duration.ofMinutes(480 + 30 * 3);
        schedule.scheduleRows.get(2).arrival.timestamp = Duration.ofMinutes(480 + 30 * 4);
        schedule.scheduleRows.get(2).departure.timestamp = Duration.ofMinutes(480 + 30 * 5);
        schedule.scheduleRows.get(3).arrival.timestamp = Duration.ofMinutes(480 + 30 * 6);
        schedule.scheduleRows.get(3).departure.timestamp = Duration.ofMinutes(480 + 30 * 7);
        schedule.scheduleRows.get(4).arrival.timestamp = Duration.ofMinutes(480 + 30 * 8);

        final LocalDate train1DepartureDate = LocalDate.of(2018, 3, 24);
        final Train train1 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train1DepartureDate).getFirst();
        assertTimes(train1.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train1DepartureDate, 8, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 1, 0, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 1, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 2, 0, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 2, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 3, 0, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 3, 30, 2),//
                createTimeWithOffset(train1DepartureDate, 8 + 4, 0, 2),//
        });

        final LocalDate train2DepartureDate = LocalDate.of(2018, 3, 25);
        final Train train2 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train2DepartureDate).getFirst();
        assertTimes(train2.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train2DepartureDate, 8, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 1, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 1, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 2, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 2, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 3, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 3, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 8 + 4, 0, 3),//
        });

        final LocalDate train3DepartureDate = LocalDate.of(2018, 3, 26);
        final Train train3 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train3DepartureDate).getFirst();
        assertTimes(train3.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train3DepartureDate, 8, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 8 + 1, 0, 3),//
                createTimeWithOffset(train3DepartureDate, 8 + 1, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 8 + 2, 0, 3),//
                createTimeWithOffset(train3DepartureDate, 8 + 2, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 8 + 3, 0, 3), //
                createTimeWithOffset(train3DepartureDate, 8 + 3, 30, 3),//
                createTimeWithOffset(train3DepartureDate, 8 + 4, 0, 3), //
        });
    }

    @Test
    @Transactional
    public void timesShouldBeOkayDuringFall() {
        final Schedule schedule = scheduleFactory.create();

        schedule.scheduleRows.getFirst().departure.timestamp = Duration.ofMinutes(30);
        schedule.scheduleRows.get(1).arrival.timestamp = Duration.ofMinutes(30 * 2);
        schedule.scheduleRows.get(1).departure.timestamp = Duration.ofMinutes(30 * 3);
        schedule.scheduleRows.get(2).arrival.timestamp = Duration.ofMinutes(30 * 4);
        schedule.scheduleRows.get(2).departure.timestamp = Duration.ofMinutes(30 * 5);
        schedule.scheduleRows.get(3).arrival.timestamp = Duration.ofMinutes(30 * 6);
        schedule.scheduleRows.get(3).departure.timestamp = Duration.ofMinutes(30 * 7);
        schedule.scheduleRows.get(4).arrival.timestamp = Duration.ofMinutes(30 * 8);

        final LocalDate train1DepartureDate = LocalDate.of(2017, 10, 28);
        final Train train1 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train1DepartureDate).getFirst();
        assertTimes(train1.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train1DepartureDate, 0, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 1, 0, 3),//
                createTimeWithOffset(train1DepartureDate, 1, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 2, 0, 3),//
                createTimeWithOffset(train1DepartureDate, 2, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 3, 0, 3),//
                createTimeWithOffset(train1DepartureDate, 3, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 4, 0, 3),//
        });

        final LocalDate train2DepartureDate = LocalDate.of(2017, 10, 29);
        final Train train2 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train2DepartureDate).getFirst();
        assertTimes(train2.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train2DepartureDate, 0, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 1, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 1, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 2, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 2, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 3, 0, 3),//
                createTimeWithOffset(train2DepartureDate, 3, 30, 3),//
                createTimeWithOffset(train2DepartureDate, 3, 0, 2),//
        });


        final LocalDate train3DepartureDate = LocalDate.of(2017, 10, 30);
        final Train train3 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train3DepartureDate).getFirst();
        assertTimes(train3.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train3DepartureDate, 0, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 1, 0, 2),//
                createTimeWithOffset(train3DepartureDate, 1, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 2, 0, 2),//
                createTimeWithOffset(train3DepartureDate, 2, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 3, 0, 2), //
                createTimeWithOffset(train3DepartureDate, 3, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 4, 0, 2), //
        });
    }

    @Test
    @Transactional
    public void timesShouldBeOkayDuringFall2() {
        final Schedule schedule = scheduleFactory.create();

        schedule.scheduleRows.getFirst().departure.timestamp = Duration.ofMinutes(480 + 30);
        schedule.scheduleRows.get(1).arrival.timestamp = Duration.ofMinutes(480 + 30 * 2);
        schedule.scheduleRows.get(1).departure.timestamp = Duration.ofMinutes(480 + 30 * 3);
        schedule.scheduleRows.get(2).arrival.timestamp = Duration.ofMinutes(480 + 30 * 4);
        schedule.scheduleRows.get(2).departure.timestamp = Duration.ofMinutes(480 + 30 * 5);
        schedule.scheduleRows.get(3).arrival.timestamp = Duration.ofMinutes(480 + 30 * 6);
        schedule.scheduleRows.get(3).departure.timestamp = Duration.ofMinutes(480 + 30 * 7);
        schedule.scheduleRows.get(4).arrival.timestamp = Duration.ofMinutes(480 + 30 * 8);

        final LocalDate train1DepartureDate = LocalDate.of(2017, 10, 28);
        final Train train1 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train1DepartureDate).getFirst();
        assertTimes(train1.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train1DepartureDate, 8, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 1, 0, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 1, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 2, 0, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 2, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 3, 0, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 3, 30, 3),//
                createTimeWithOffset(train1DepartureDate, 8 + 4, 0, 3),//
        });

        final LocalDate train2DepartureDate = LocalDate.of(2017, 10, 29);
        final Train train2 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train2DepartureDate).getFirst();
        assertTimes(train2.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train2DepartureDate, 8, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 1, 0, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 1, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 2, 0, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 2, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 3, 0, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 3, 30, 2),//
                createTimeWithOffset(train2DepartureDate, 8 + 4, 0, 2),//
        });

        final LocalDate train3DepartureDate = LocalDate.of(2017, 10, 30);
        final Train train3 = extract(Collections.emptyList(), Lists.newArrayList(schedule), train3DepartureDate).getFirst();
        assertTimes(train3.timeTableRows, new ZonedDateTime[]{//
                createTimeWithOffset(train3DepartureDate, 8, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 8 + 1, 0, 2),//
                createTimeWithOffset(train3DepartureDate, 8 + 1, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 8 + 2, 0, 2),//
                createTimeWithOffset(train3DepartureDate, 8 + 2, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 8 + 3, 0, 2), //
                createTimeWithOffset(train3DepartureDate, 8 + 3, 30, 2),//
                createTimeWithOffset(train3DepartureDate, 8 + 4, 0, 2), //
        });
    }

    private ZonedDateTime createTimeWithOffset(final LocalDate localdate, final int hour, final int minute, final int offsetInHours) {
        return ZonedDateTime.ofInstant(LocalDateTime.of(localdate, LocalTime.of(hour, minute)), ZoneOffset.ofHours(offsetInHours),
                ZoneId.of("Europe/Helsinki"));
    }

    @Test
    @Transactional
    public void alreadyExtractedShouldNotBeReExtracted() {
        final Schedule schedule = scheduleFactory.create();

        Assertions.assertEquals(1, extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate).size());
        Assertions.assertEquals(0, extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate).size());
    }

    @Test
    @Transactional
    public void alreadySimilarExtractedShouldNotBeReExtracted() {
        final Schedule schedule = scheduleFactory.create();
        schedule.id = 1L;
        schedule.version = 1L;
        schedule.capacityId = "TEST1";

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.capacityId = "TEST2";

        Assertions.assertEquals(1, extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate).size());
        Assertions.assertEquals(0, extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate).size());
    }

    @Test
    @Transactional
    public void firstOkayThenUpdatedAdhocWithDifferentCapacityId() {
        final Schedule schedule = scheduleFactory.create();
        schedule.timetableType = Train.TimetableType.ADHOC;
        schedule.startDate = extractDate;
        schedule.endDate = null;

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.timetableType = Train.TimetableType.ADHOC;
        schedule2.startDate = extractDate;
        schedule2.endDate = null;
        schedule2.capacityId = "NEW_CAPACITY";
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.scheduleRows.getFirst().departure.timestamp = schedule2.scheduleRows.getFirst().departure.timestamp.minusMinutes(1);

        Assertions.assertEquals(1, extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate).size());
        Assertions.assertEquals(1, extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate).size());
    }

    @Test
    @Transactional
    public void partCancellationShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();
        schedule.id = 1L;
        schedule.version = 1L;
        schedule.capacityId = "TEST1";

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.capacityId = "TEST2";
        final ScheduleCancellation scheduleCancellation = new ScheduleCancellation();
        scheduleCancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.PARTIALLY;
        scheduleCancellation.startDate = extractDate;
        scheduleCancellation.endDate = extractDate;
        scheduleCancellation.cancelledRows.add(schedule2.scheduleRows.getFirst().departure);
        scheduleCancellation.cancelledRows.add(schedule2.scheduleRows.get(1).arrival);
        schedule2.scheduleCancellations.add(scheduleCancellation);

        Assertions.assertEquals(1, extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate).size());
        Assertions.assertEquals(1, extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate).size());
    }

    @Test
    @Transactional
    public void firstOkayThenCancelledShouldResultInCancelled() {
        final Schedule schedule = scheduleFactory.create();
        schedule.version = 1L;
        schedule.id = 1L;

        final ScheduleCancellation scheduleCancellation = new ScheduleCancellation();
        scheduleCancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        scheduleCancellation.startDate = extractDate;
        scheduleCancellation.endDate = extractDate;
        final Schedule schedule2 = scheduleFactory.create();
        schedule2.scheduleCancellations.add(scheduleCancellation);
        schedule2.version = 2L;
        schedule2.id = 2L;

        final Schedule schedule3 = scheduleFactory.create();
        schedule3.version = 3L;
        schedule3.id = 3L;

        final Train firstTrain = extract(Collections.emptyList(), List.of(schedule), extractDate).getFirst();
        Assertions.assertFalse(firstTrain.cancelled);
        Assertions.assertNull(firstTrain.deleted);

        final Train secondTrain = extract(Collections.emptyList(), List.of(schedule, schedule2), extractDate).getFirst();
        Assertions.assertTrue(secondTrain.cancelled);
        Assertions.assertEquals(true, secondTrain.deleted);

        final Train thirdTrain = extract(Collections.emptyList(), List.of(schedule, schedule2, schedule3), extractDate).getFirst();
        Assertions.assertFalse(thirdTrain.cancelled);
        Assertions.assertNull(thirdTrain.deleted);
    }

    @Test
    @Transactional
    public void firstCancelledThenOkayShouldResultInATrain() {
        final ScheduleCancellation scheduleCancellation = new ScheduleCancellation();
        scheduleCancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        scheduleCancellation.startDate = extractDate;
        scheduleCancellation.endDate = extractDate;
        final Schedule cancalledSchedule = scheduleFactory.create();
        cancalledSchedule.scheduleCancellations.add(scheduleCancellation);

        Assertions.assertEquals(0, extract(Collections.emptyList(), Lists.newArrayList(cancalledSchedule), extractDate).size());

        final Schedule normalSchedule = scheduleFactory.create();
        normalSchedule.version = 2L;
        normalSchedule.id = 2L;

        final Train firstTrain = extract(Collections.emptyList(), Lists.newArrayList(cancalledSchedule, normalSchedule), extractDate).getFirst();
        Assertions.assertFalse(firstTrain.cancelled);
    }

    @Test
    @Transactional
    public void stationChangeShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.scheduleRows.getFirst().station.stationShortCode = "ABC";

        final Schedule schedule3 = scheduleFactory.create();
        schedule3.id = 3L;
        schedule3.version = 3L;
        schedule3.scheduleRows.getFirst().station.stationShortCode = "DEF";

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
        Assertions.assertEquals("ABC", changedTrainsAfter.getFirst().timeTableRows.getFirst().station.stationShortCode);

        final List<Train> changedTrainsLastTime = extract(Collections.emptyList(),
                Lists.newArrayList(schedule, schedule2, schedule3), extractDate);
        Assertions.assertEquals(1, changedTrainsLastTime.size());
        Assertions.assertEquals("DEF", changedTrainsLastTime.getFirst().timeTableRows.getFirst().station.stationShortCode);
    }

    @Test
    @Transactional
    public void stopTypeShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.scheduleRows.get(1).arrival.stopType = ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;
        schedule2.scheduleRows.get(1).departure.stopType = ScheduleRow.ScheduleRowStopType.NONCOMMERCIAL;

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
    }

    @Test
    @Transactional
    public void passtroughShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.scheduleRows.get(1).arrival.stopType = ScheduleRow.ScheduleRowStopType.PASS;
        schedule2.scheduleRows.get(1).departure.stopType = ScheduleRow.ScheduleRowStopType.PASS;

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
    }

    @Test
    @Transactional
    public void operatorChangeShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.operator = new Operator();
        schedule2.operator.operatorShortCode = "TEST_OP_2";
        schedule2.operator.operatorUICCode = 1234;

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
    }

    @Test
    @Transactional
    public void trainCategoryChangeShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;

        schedule2.trainCategory = new TrainCategory();
        schedule2.trainCategory.name = "ABCDE";
        schedule2.trainCategory.id = 1234L;

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
    }

    @Test
    @Transactional
    public void trainTypeChangeShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;

        schedule2.trainType = new TrainType();
        schedule2.trainType.name = "ABCDE";
        schedule2.trainType.id = 1234L;

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
    }

    @Test
    @Transactional
    public void commuterLineChangeShouldUpdate() {
        final Schedule schedule = scheduleFactory.create();
        schedule.commuterLineId = null;

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.id = 2L;
        schedule2.version = 2L;
        schedule2.commuterLineId = "ABCDE";

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());

        final List<Train> changedTrainsAfter = extract(Collections.emptyList(), Lists.newArrayList(schedule, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrainsAfter.size());
    }

    @Test
    @Transactional
    public void simpleExtractShouldBeOkay() {
        final Schedule schedule = scheduleFactory.create();

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(1, changedTrains.size());
    }

    @Test
    @Transactional
    public void newerAdhocShouldBeSelected() {
        final Schedule schedule1 = scheduleFactory.create();
        schedule1.timetableType = Train.TimetableType.ADHOC;
        schedule1.startDate = extractDate;

        final Schedule schedule2 = scheduleFactory.create();
        schedule2.timetableType = Train.TimetableType.ADHOC;
        schedule2.startDate = extractDate;
        schedule2.scheduleRows.getFirst().station.stationShortCode = "ABC";
        schedule2.id = 2L;
        schedule2.version = 2L;

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule1, schedule2), extractDate);
        Assertions.assertEquals(1, changedTrains.size());
        Assertions.assertEquals("ABC", changedTrains.getFirst().timeTableRows.getFirst().station.stationShortCode);
    }

    @Test
    @Transactional
    public void firstBigScheduleThenSmallScheduleShouldReturnWholeRange() {
        final Schedule bigSchedule = scheduleFactory.create();
        bigSchedule.startDate = LocalDate.of(2017, 6, 1);
        bigSchedule.endDate = LocalDate.of(2017, 6, 4);
        bigSchedule.effectiveFrom = LocalDate.of(2017, 6, 1);

        final Schedule smallSchedule = scheduleFactory.create();
        smallSchedule.startDate = LocalDate.of(2017, 6, 3);
        smallSchedule.endDate = LocalDate.of(2017, 6, 4);
        smallSchedule.effectiveFrom = LocalDate.of(2017, 6, 3);
        smallSchedule.id = 2L;
        smallSchedule.version = 2L;

        final List<Schedule> scheduleListAfter = Lists.newArrayList(bigSchedule, smallSchedule);
        final List<Train> changedTrains = new ArrayList<>();
        changedTrains.addAll(extract(Collections.emptyList(), scheduleListAfter, LocalDate.of(2017, 5, 31)));
        changedTrains.addAll(extract(Collections.emptyList(), scheduleListAfter, LocalDate.of(2017, 6, 1)));
        changedTrains.addAll(extract(Collections.emptyList(), scheduleListAfter, LocalDate.of(2017, 6, 2)));
        changedTrains.addAll(extract(Collections.emptyList(), scheduleListAfter, LocalDate.of(2017, 6, 3)));
        changedTrains.addAll(extract(Collections.emptyList(), scheduleListAfter, LocalDate.of(2017, 6, 4)));
        changedTrains.addAll(extract(Collections.emptyList(), scheduleListAfter, LocalDate.of(2017, 6, 5)));

        Assertions.assertEquals(4, changedTrains.size());
        for (final Train changedTrain : changedTrains) {
            Assertions.assertFalse(changedTrain.cancelled);
        }
    }

    @Test
    @Transactional
    public void firstOkayThenOfTypePShouldWork() {
        final Schedule firstSchedule = scheduleFactory.create();
        firstSchedule.startDate = LocalDate.of(2017, 3, 27);
        firstSchedule.endDate = LocalDate.of(2017, 12, 8);
        firstSchedule.effectiveFrom = LocalDate.of(2017, 3, 26);


        final Schedule secondSchedule = scheduleFactory.create();
        secondSchedule.startDate = LocalDate.of(2017, 3, 27);
        secondSchedule.endDate = LocalDate.of(2017, 12, 8);
        secondSchedule.effectiveFrom = LocalDate.of(2017, 6, 19);
        secondSchedule.id = 2L;
        secondSchedule.version = 2L;
        secondSchedule.changeType = "P";

        // first one in effect
        final List<Train> schedules = extract(Collections.emptyList(), Lists.newArrayList(firstSchedule, secondSchedule),
                LocalDate.of(2017, 4, 25));
        final List<Train> trains = schedules;
        Assertions.assertEquals(1, trains.size());

        // P in effect, not trains
        final List<Train> noTrains = extract(Collections.emptyList(), Lists.newArrayList(firstSchedule, secondSchedule),
                LocalDate.of(2017, 7, 25));
        Assertions.assertEquals(0, noTrains.size());

    }

    @Test
    @Transactional
    public void cancelledShouldReturnNoTrain() {
        final ScheduleCancellation scheduleCancellation = new ScheduleCancellation();
        scheduleCancellation.scheduleCancellationType = ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY;
        scheduleCancellation.startDate = extractDate;
        scheduleCancellation.endDate = extractDate;
        final Schedule schedule = scheduleFactory.create();
        schedule.scheduleCancellations.add(scheduleCancellation);

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(0, changedTrains.size());
    }

    @Test
    @Transactional
    public void scheduleWithExceptionShouldReturnNoTrain() {
        final ScheduleException scheduleException = new ScheduleException();
        scheduleException.isRun = false;
        scheduleException.date = extractDate;
        final Schedule schedule = scheduleFactory.create();
        schedule.scheduleExceptions.add(scheduleException);

        final List<Train> changedTrains = extract(Collections.emptyList(), Lists.newArrayList(schedule), extractDate);
        Assertions.assertEquals(0, changedTrains.size());
    }

    @Test
    @Transactional
    public void multipleSchedulesWithSameKapasiteettiId() throws Exception {
        final Schedule[] schedules = httpInputObjectMapper.readValue(new ClassPathResource("schedules-kapasiteetti-id.json").getFile(), Schedule[].class);

        final List<Train> trains = extract(Collections.emptyList(), List.of(schedules),
                LocalDate.of(2024, 12, 20));

        Assertions.assertEquals(1, trains.size());
        Assertions.assertEquals(265L, trains.getFirst().id.trainNumber);
    }

    private void assertTimes(final List<TimeTableRow> timeTableRows, final ZonedDateTime[] times) {
        Assertions.assertEquals(timeTableRows.size(), times.length);

        for (int i = 0; i < timeTableRows.size(); i++) {
            final TimeTableRow timeTableRow = timeTableRows.get(i);
            Assertions.assertTrue(timeTableRow.scheduledTime.isEqual(times[i]));
        }
    }
}
