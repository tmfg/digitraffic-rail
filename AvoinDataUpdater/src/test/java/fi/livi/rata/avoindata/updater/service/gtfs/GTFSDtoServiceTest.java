package fi.livi.rata.avoindata.updater.service.gtfs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.*;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;

public class GTFSDtoServiceTest extends BaseTest {
    public static final String KOKKOLA_UIC = "KOK";
    public static final String HELSINKI_UIC = "HKI";
    public static final String OULU_UIC = "OL";
    public static final String TAMPERE_UIC = "TPE";
    public static final String YLIVIESKA_UIC = "YV";
    private static final String JOENSUU_UIC = "JNS";

    @Autowired
    private GTFSEntityService gtfsService;

    @Autowired
    private GTFSWritingService gtfsWritingService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DateProvider dp;

    @Value("classpath:gtfs/263.json")
    private Resource schedules_263;

    @Value("classpath:gtfs/4110.json")
    private Resource schedules_4110;

    @Value("classpath:gtfs/9.json")
    private Resource schedules_9;

    @Value("classpath:gtfs/1.json")
    private Resource schedules_1;

    @Value("classpath:gtfs/20.json")
    private Resource schedules_20;

    @Value("classpath:gtfs/27.json")
    private Resource schedules_27;


    @Before
    public void setup() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2017, 9, 9));
        given(dp.nowInHelsinki()).willReturn(ZonedDateTime.now());

        //To write test json for a train:
        //                List<JsonNode> nodes = new ArrayList<>();
        //                final long trainNumber = 27L;
        //                for (final JsonNode jsonNode : objectMapper.readTree(new File("C:\\temp\\schedules\\regular-schedules.json"))) {
        //                    if (jsonNode.get("aikataulunJunanumero").get("junanumero").longValue() == trainNumber) {
        //                        nodes.add(jsonNode);
        //                    }
        //                }
        //                for (final JsonNode jsonNode : objectMapper.readTree(new File("C:\\temp\\schedules\\adhoc-schedules.json"))) {
        //                    if (jsonNode.get("aikataulunJunanumero").get("junanumero").longValue() == trainNumber) {
        //                        nodes.add(jsonNode);
        //                    }
        //                }
        //                objectMapper.writeValue(new File(String.format("%s.json", trainNumber)), nodes);
    }

    @Test
    @Transactional
    public void stopTypesShouldBeCorrect() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_1.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        final Trip firstTrip = tripsByServiceId.get("1_2017-09-02_2017-12-09");

        assertTrip(firstTrip, LocalDate.of(2017, 9, 2), LocalDate.of(2017, 12, 9), true, true, true, true, true, true, true);

        assertTripStops(firstTrip, HELSINKI_UIC, JOENSUU_UIC);

        Map<String, Integer> stopTypes = new HashMap<>();
        stopTypes.put("HAA", 1);
        stopTypes.put("HKH", 1);
        stopTypes.put("HLT", 1);
        stopTypes.put("HNA", 1);
        stopTypes.put("HNN", 1);
        stopTypes.put("HSL", 1);
        stopTypes.put("HVK", 1);
        stopTypes.put("IMR", 0);
        stopTypes.put("IMT", 1);
        stopTypes.put("JNS", 0);
        stopTypes.put("JTS", 0);
        stopTypes.put("KÄP", 1);
        stopTypes.put("KA", 1);
        stopTypes.put("KE", 1);
        stopTypes.put("KIJ", 1);
        stopTypes.put("KIT", 0);
        stopTypes.put("KJR", 1);
        stopTypes.put("KPA", 1);
        stopTypes.put("KRA", 1);
        stopTypes.put("KRS", 1);
        stopTypes.put("KSU", 1);
        stopTypes.put("KTH", 1);
        stopTypes.put("KTI", 0);
        stopTypes.put("KUV", 1);
        stopTypes.put("KV", 0);
        stopTypes.put("KVY", 1);
        stopTypes.put("KYT", 1);
        stopTypes.put("LÄ", 1);
        stopTypes.put("LH", 0);
        stopTypes.put("LR", 0);
        stopTypes.put("LRS", 1);
        stopTypes.put("MKA", 1);
        stopTypes.put("MKO", 1);
        stopTypes.put("MLÄ", 1);
        stopTypes.put("ML", 1);
        stopTypes.put("NMÄ", 1);
        stopTypes.put("NSL", 1);
        stopTypes.put("NTH", 1);
        stopTypes.put("OLK", 1);
        stopTypes.put("PAR", 0);
        stopTypes.put("PLA", 1);
        stopTypes.put("PLT", 1);
        stopTypes.put("PMK", 1);
        stopTypes.put("POI", 1);
        stopTypes.put("PSL", 0);
        stopTypes.put("PUS", 1);
        stopTypes.put("RAH", 1);
        stopTypes.put("RAS", 1);
        stopTypes.put("RJÄ", 1);
        stopTypes.put("RKL", 1);
        stopTypes.put("SÄ", 1);
        stopTypes.put("SAV", 1);
        stopTypes.put("SIP", 1);
        stopTypes.put("SMÄ", 1);
        stopTypes.put("SPL", 0);
        stopTypes.put("SR", 1);
        stopTypes.put("SUL", 1);
        stopTypes.put("TA", 1);
        stopTypes.put("TAP", 1);
        stopTypes.put("TKK", 1);
        stopTypes.put("TKL", 0);
        stopTypes.put("TNA", 1);
        stopTypes.put("TRÄ", 1);
        stopTypes.put("UKÄ", 1);
        stopTypes.put("UTI", 1);
        stopTypes.put("VLH", 1);
        stopTypes.put("HKI", 0);

        Assert.assertEquals(stopTypes.size(), firstTrip.stopTimes.size());

        for (int i = 0; i < firstTrip.stopTimes.size(); i++) {
            final StopTime stopTime = firstTrip.stopTimes.get(i);
            Assert.assertEquals(stopTime.stopId, stopTypes.get(stopTime.stopId).intValue(), stopTime.dropoffType);
            Assert.assertEquals(stopTime.stopId, stopTypes.get(stopTime.stopId).intValue(), stopTime.pickupType);
        }

    }

    @Test
    @Transactional
    public void differentRouteShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_1.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        final Trip firstTrip = tripsByServiceId.get("1_2017-09-02_2017-12-09");
        final Trip secondTrip = tripsByServiceId.get("1_2017-12-10_2018-06-17");
        final Trip thirdTrip = tripsByServiceId.get("1_2018-06-18_2018-12-31");

        assertTrip(firstTrip, LocalDate.of(2017, 9, 2), LocalDate.of(2017, 12, 9), true, true, true, true, true, true, true);
        assertTrip(secondTrip, LocalDate.of(2017, 12, 10), LocalDate.of(2018, 6, 17), true, true, true, true, true, true, true);
        assertTrip(thirdTrip, LocalDate.of(2018, 6, 18), LocalDate.of(2018, 12, 31), true, true, true, true, true, true, true);

    }

    @Test
    @Transactional
    public void train9ShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_9.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 71);

        final ImmutableMap<String, Trip> trips = Maps.uniqueIndex(gtfsDto.trips, s -> s.tripId);

        Assert.assertNotNull(trips.get(String.format("%s_%s_%s", 9L, LocalDate.of(2018, 6, 18), LocalDate.of(2018, 12, 31))));

        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 11))));        // ma
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 12))));        // ti
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 13))));        // ke
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 14))));        // to
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 15))));        // pe
        Assert.assertNotNull(trips.get(String.format("%s_%2$s_%2$s_replacement", 9L, LocalDate.of(2018, 6, 16))));     // la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 17))));        // su

        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 18))));        // ma
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 19))));        // ti
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 20))));        // ke
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 21))));        // to
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 22))));        // pe
        Assert.assertNotNull(trips.get(String.format("%s_%2$s_%2$s_replacement", 9L, LocalDate.of(2018, 6, 23))));     // la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 24))));        // su

        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 25))));        // ma
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 26))));        // ti
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 27))));        // ke
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 28))));        // to
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 6, 29))));        // pe
        Assert.assertNotNull(trips.get(String.format("%s_%2$s_%2$s_replacement", 9L, LocalDate.of(2018, 6, 30))));     // la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, LocalDate.of(2018, 7, 1))));         // su
    }

    @Test
    @Transactional
    public void partialCancellationShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_27.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        for (Stop stop : gtfsDto.stops) {
            stop.source = new Station();
            stop.source.name = stop.stopCode;
        }

        gtfsWritingService.writeGTFSFiles(gtfsDto);

        assertTrips(gtfsDto.trips, 12);

        final ImmutableMap<String, Trip> trips = Maps.uniqueIndex(gtfsDto.trips, s -> s.tripId);

        assertTripStops(trips.get(String.format("%s_%s_%s_replacement", 27L, LocalDate.of(2017, 10, 7), LocalDate.of(2017, 10, 7))), HELSINKI_UIC,
                TAMPERE_UIC);
    }

    @Test
    @Transactional
    public void train20ShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_20.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 12);

        final ImmutableMap<String, Trip> trips = Maps.uniqueIndex(gtfsDto.trips, s -> s.tripId);

        assertTripStops(trips.get(String.format("%s_%s_%s", 20L, LocalDate.of(2017, 9, 2), LocalDate.of(2017, 12, 10))), OULU_UIC,
                HELSINKI_UIC);
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 9, 9))), KOKKOLA_UIC,
                HELSINKI_UIC);    // 9.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 9, 16))), KOKKOLA_UIC,
                HELSINKI_UIC);         // 16.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 9, 23))), KOKKOLA_UIC,
                HELSINKI_UIC);      // 23.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 9, 30))), KOKKOLA_UIC,
                HELSINKI_UIC);       // 30.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 10, 7))), TAMPERE_UIC,
                HELSINKI_UIC);         // 7.10. la TPE-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 10, 14))), KOKKOLA_UIC,
                HELSINKI_UIC);      // 14.10. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 10, 21))), KOKKOLA_UIC,
                HELSINKI_UIC);       // 21.10. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, LocalDate.of(2017, 10, 28))), KOKKOLA_UIC,
                HELSINKI_UIC);       // 28.10. la YVI-HKI
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, LocalDate.of(2017, 11, 4))));            // 4.11. la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, LocalDate.of(2017, 11, 11))));           // 11.11. la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, LocalDate.of(2017, 11, 18))));           // 18.11. la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, LocalDate.of(2017, 11, 25))));           // 25.11. la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, LocalDate.of(2017, 12, 2))));            // 2.12. la
        Assert.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, LocalDate.of(2017, 12, 9))));            // 9.12. la

        assertTripStops(trips.get(String.format("%s_%s_%s", 20L, LocalDate.of(2017, 12, 11), LocalDate.of(2018, 6, 17))), OULU_UIC,
                HELSINKI_UIC);
        assertTripStops(trips.get(String.format("%s_%s_%s", 20L, LocalDate.of(2018, 6, 18), LocalDate.of(2018, 12, 31))), OULU_UIC,
                HELSINKI_UIC);
    }

    @Test
    @Transactional
    public void cancelledSchedulesShouldNotAffect() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_4110.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);
    }

    @Test
    @Transactional
    public void train263ShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_263.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertAgencies(gtfsDto.agencies, 10);
        assertRoutes(gtfsDto.routes, "HKI - KLI");
        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);

        final Trip firstTrip = tripsByServiceId.get("263_2017-09-02_2017-12-14");
        assertTrip(firstTrip, LocalDate.of(2017, 9, 2), LocalDate.of(2017, 12, 14), false, false, true, false, true, true,
                false);

        Assert.assertEquals(1, firstTrip.calendar.calendarDates.size());
        Assert.assertEquals(2, firstTrip.calendar.calendarDates.iterator().next().exceptionType);
        Assert.assertEquals(LocalDate.of(2017, 10, 7), firstTrip.calendar.calendarDates.iterator().next().date);

        final Trip secondTrip = tripsByServiceId.get("263_2017-12-15_2018-06-17");
        assertTrip(secondTrip, LocalDate.of(2017, 12, 15), LocalDate.of(2018, 6, 17), true, false, true, false, true, false, false);
        Assert.assertEquals(35, secondTrip.calendar.calendarDates.size());
        Assert.assertEquals(1, Collections2.filter(secondTrip.calendar.calendarDates, cd -> cd.exceptionType == 1).size());
        Assert.assertEquals(34, Collections2.filter(secondTrip.calendar.calendarDates, cd -> cd.exceptionType == 2).size());

        final Trip thirdTrip = tripsByServiceId.get("263_2018-06-18_2018-12-31");
        assertTrip(thirdTrip, LocalDate.of(2018, 06, 18), LocalDate.of(2018, 12, 31), true, false, false, false, true, true, false);
        Assert.assertEquals(0, thirdTrip.calendar.calendarDates.size());

        Assert.assertEquals(125 - 8, firstTrip.stopTimes.size());
    }

    private void assertTripStops(Trip trip, String departureStopId, String arrivalStopId) {
        Assert.assertEquals(departureStopId, trip.stopTimes.get(0).stopId);
        Assert.assertEquals(arrivalStopId, trip.stopTimes.get(trip.stopTimes.size() - 1).stopId);
    }

    private void assertTrip(final Trip trip, final LocalDate startDate, final LocalDate endDate, final boolean monday,
            final boolean tuesday, final boolean wednesday, final boolean thursday, final boolean friday, final boolean saturday,
            final boolean sunday) {
        Assert.assertEquals(startDate, trip.calendar.startDate);
        Assert.assertEquals(endDate, trip.calendar.endDate);
        Assert.assertEquals(monday, trip.calendar.monday);
        Assert.assertEquals(tuesday, trip.calendar.tuesday);
        Assert.assertEquals(wednesday, trip.calendar.wednesday);
        Assert.assertEquals(thursday, trip.calendar.thursday);
        Assert.assertEquals(friday, trip.calendar.friday);
        Assert.assertEquals(saturday, trip.calendar.saturday);
        Assert.assertEquals(sunday, trip.calendar.sunday);
    }

    private void assertTrips(final List<Trip> trips, int expectedTripCount) {
        final ImmutableMap<String, Trip> tripsAreUnique = Maps.uniqueIndex(trips, s -> s.tripId);
        Assert.assertEquals(expectedTripCount, trips.size());
    }

    private void assertRoutes(final List<Route> routes, final String shortName) {
        Assert.assertEquals(1, routes.size());
        final Route route = routes.iterator().next();
        Assert.assertEquals(10, route.agencyId);
        Assert.assertEquals(102, route.type);
        Assert.assertEquals(shortName, route.shortName);
    }

    private void assertAgencies(final List<Agency> agencies, final int agencyId) {
        Assert.assertEquals(1, agencies.size());
        final Agency agency = agencies.iterator().next();
        Assert.assertEquals("vr", agency.name);
        Assert.assertEquals(agencyId, agency.id);
        Assert.assertEquals("Europe/Helsinki", agency.timezone);
    }
}