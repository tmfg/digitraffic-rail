package fi.livi.rata.avoindata.updater.deserializers.timetable;

import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.config.HttpInputObjectMapper;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleToTrainConverter;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleCancellation;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;

public class ScheduleDeserializerTest extends BaseTest {
    @Autowired
    private HttpInputObjectMapper httpInputObjectMapper;

    @Autowired
    private ScheduleToTrainConverter scheduleToTrainConverter;

    @Test
    public void testDeserializer() throws Exception {
        final Schedule schedule = httpInputObjectMapper.readValue(new ClassPathResource("schedule.json").getFile(), Schedule.class);

        assertSchedule(schedule);

        final ArrayList<ScheduleRow> scheduleRows = new ArrayList<>(schedule.scheduleRows);
        Collections.sort(scheduleRows, (o1, o2) -> Long.compare(o1.id, o2.id));

        assertScheduleRows(scheduleRows);

        final ArrayList<ScheduleCancellation> scheduleCancellations = new ArrayList<>(schedule.scheduleCancellations);
        Collections.sort(scheduleCancellations, (o1, o2) -> Long.compare(o1.id, o2.id));

        assertWholeDayCancellation(scheduleCancellations.get(0));
        assertPartialCancellation(scheduleCancellations.get(2));
    }

    @Test
    public void overnightScheduleIntegrationTest() throws Exception {
        final Schedule schedule = httpInputObjectMapper.readValue(new ClassPathResource("schedule_overnight.json").getFile(),
                Schedule.class);


        Assert.assertEquals("KLI", schedule.scheduleRows.get(0).station.stationShortCode);
        Assert.assertEquals("HKI", schedule.scheduleRows.get(116).station.stationShortCode);

        Assert.assertEquals(Duration.parse("PT18H20M"), schedule.scheduleRows.get(0).departure.timestamp);
        Assert.assertEquals(Duration.parse("PT33H38M"), schedule.scheduleRows.get(116).arrival.timestamp);

    }

    private void assertSchedule(final Schedule schedule) {
        Assert.assertEquals(4749, schedule.trainNumber.longValue());
        Assert.assertEquals(LocalDate.of(2016, 12, 12), schedule.startDate);
        Assert.assertEquals(LocalDate.of(2017, 6, 16), schedule.endDate);

        Assert.assertEquals(10, schedule.operator.operatorUICCode);
        Assert.assertEquals("vr", schedule.operator.operatorShortCode);

        Assert.assertEquals(46, schedule.trainType.id.intValue());
        Assert.assertEquals("T", schedule.trainType.name);

        Assert.assertEquals(3, schedule.trainCategory.id.intValue());
        Assert.assertEquals("Cargo", schedule.trainCategory.name);
    }

    private void assertScheduleRows(final ArrayList<ScheduleRow> scheduleRows) {
        assertScheduleRow(scheduleRows.get(0), null, Duration.parse("PT18H31M"), "NRL");
        assertScheduleRow(scheduleRows.get(1), Duration.parse("PT18H51M"), Duration.parse("PT18H51M"), "TOH");
        assertScheduleRow(scheduleRows.get(2), Duration.parse("PT19H03M56S"), Duration.parse("PT19H03M56S"), "VSO");
        assertScheduleRow(scheduleRows.get(3), Duration.parse("PT19H07M"), Duration.parse("PT19H07M"), "SÄ");
        assertScheduleRow(scheduleRows.get(4), Duration.parse("PT19H13M"), Duration.parse("PT19H13M"), "TKK");
        assertScheduleRow(scheduleRows.get(5), Duration.parse("PT19H23M"), Duration.parse("PT19H23M"), "HSL");
        assertScheduleRow(scheduleRows.get(6), Duration.parse("PT19H35M"), Duration.parse("PT19H35M"), "NTH");
        assertScheduleRow(scheduleRows.get(7), Duration.parse("PT19H47M"), Duration.parse("PT19H47M"), "SUL");
        assertScheduleRow(scheduleRows.get(8), Duration.parse("PT19H50M"), Duration.parse("PT19H50M"), "PLT");
        assertScheduleRow(scheduleRows.get(9), Duration.parse("PT19H53M"), Duration.parse("PT20H08M"), "JNS");
        assertScheduleRow(scheduleRows.get(10), Duration.parse("PT20H09M19S"), Duration.parse("PT20H9M19S"), "JNS_V101");
        assertScheduleRow(scheduleRows.get(11), Duration.parse("PT20H27M"), Duration.parse("PT20H27M"), "KHI");
        assertScheduleRow(scheduleRows.get(12), Duration.parse("PT20H47M"), Duration.parse("PT20H47M"), "ENO");
        assertScheduleRow(scheduleRows.get(13), Duration.parse("PT21H03M"), null, "UIM");
    }

    private void assertScheduleRow(final ScheduleRow scheduleRow, final Duration arrivalTime, final Duration departureTime,
            final String stationShortCode) {
        if (departureTime != null) {
            Assert.assertEquals(departureTime, scheduleRow.departure.timestamp);
        }
        if (arrivalTime != null) {
            Assert.assertEquals(arrivalTime, scheduleRow.arrival.timestamp);
        }
        Assert.assertEquals(scheduleRow.station.stationShortCode, stationShortCode);
    }

    private void assertWholeDayCancellation(final ScheduleCancellation wholedayCancellation) {
        Assert.assertEquals(LocalDate.of(2016, 12, 26), wholedayCancellation.startDate);
        Assert.assertEquals(LocalDate.of(2016, 12, 26), wholedayCancellation.endDate);
        Assert.assertEquals(ScheduleCancellation.ScheduleCancellationType.WHOLE_DAY, wholedayCancellation.scheduleCancellationType);
    }

    private void assertPartialCancellation(final ScheduleCancellation partialDayCancellation) {
        final ArrayList<ScheduleRowPart> scheduleRowParts = new ArrayList<>(partialDayCancellation.cancelledRows);
        Collections.sort(scheduleRowParts, (o1, o2) -> Long.compare(o1.id, o2.id));

        Assert.assertEquals("SUL", scheduleRowParts.get(0).scheduleRow.station.stationShortCode);
        Assert.assertEquals("PLT", scheduleRowParts.get(1).scheduleRow.station.stationShortCode);
        Assert.assertEquals("PLT", scheduleRowParts.get(2).scheduleRow.station.stationShortCode);
        Assert.assertEquals("JNS", scheduleRowParts.get(3).scheduleRow.station.stationShortCode);
        Assert.assertEquals("JNS", scheduleRowParts.get(4).scheduleRow.station.stationShortCode);
        Assert.assertEquals("JNS_V101", scheduleRowParts.get(5).scheduleRow.station.stationShortCode);
        Assert.assertEquals("JNS_V101", scheduleRowParts.get(6).scheduleRow.station.stationShortCode);
        Assert.assertEquals("KHI", scheduleRowParts.get(7).scheduleRow.station.stationShortCode);
        Assert.assertEquals("KHI", scheduleRowParts.get(8).scheduleRow.station.stationShortCode);
        Assert.assertEquals("ENO", scheduleRowParts.get(9).scheduleRow.station.stationShortCode);
        Assert.assertEquals("ENO", scheduleRowParts.get(10).scheduleRow.station.stationShortCode);
        Assert.assertEquals("UIM", scheduleRowParts.get(11).scheduleRow.station.stationShortCode);
    }


}