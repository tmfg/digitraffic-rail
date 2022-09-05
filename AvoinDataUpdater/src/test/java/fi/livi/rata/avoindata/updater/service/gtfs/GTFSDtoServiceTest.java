package fi.livi.rata.avoindata.updater.service.gtfs;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STATION;
import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STOP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParser;
import org.junit.jupiter.params.shadow.com.univocity.parsers.csv.CsvParserSettings;
import org.locationtech.jts.geom.MultiLineString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import fi.livi.rata.avoindata.common.domain.gtfs.SimpleTimeTableRow;

import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Agency;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.InfraApiPlatform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Platform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.PlatformData;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Route;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Transactional
@Sql({ "/gtfs/import_test_stations.sql" })
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
    private InfraApiPlatformService infraApiPlatformService;
    @MockBean
    private GTFSShapeService gtfsShapeService;

    @MockBean
    private TimeTableRowService timeTableRowService;

    @MockBean
    private PlatformDataService platformDataService;

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

    @Value("classpath:gtfs/910.json")
    private Resource schedules_910;

    @Value("classpath:gtfs/66.json")
    private Resource schedules_66;

    @Value("classpath:gtfs/66_ttr.json")
    private Resource timetablerows_66;

    @Value("classpath:gtfs/141_151.json")
    private Resource schedules_141_151;

    @Value("classpath:gtfs/9924.json")
    private Resource schedules_9924;

    @Value("classpath:gtfs/781.json")
    private Resource schedules_781;

    @Value("classpath:gtfs/59.json")
    private Resource schedules_59;


    @BeforeEach
    public void setup() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2017, 9, 9));
        given(dp.nowInHelsinki()).willReturn(ZonedDateTime.now());

        given(gtfsShapeService.createShapesFromTrips(any(),any())).willReturn(new ArrayList<>());

        given(platformDataService.getCurrentPlatformData()).willReturn(getMockPlatformData());
    }

    @Test
    @Transactional
    public void train59ShouldBeOkay() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2020, 12, 11));

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_59.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules.stream().filter(s -> s.timetableType == Train.TimetableType.REGULAR).collect(Collectors.toList()));

        List<Trip> trips = gtfsDto.trips.stream().filter(s -> s.tripId.startsWith("59_20210110_20210110_replacement")).collect(Collectors.toList());
        assertTrips(trips, 1);
    }

    @Test
    @Transactional
    public void train910ShouldBeOkay() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2019, 11, 27));

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_910.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        //Original schedule is completely cancelled, so there should be three trips
        assertTrips(gtfsDto.trips, 2);
    }

    @Test
    @Transactional
    public void train781ShouldBeOkay() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2020, 10, 13));

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_781.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);
        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        Assertions.assertNotNull(tripsByServiceId.get("781_20210328_20211030"));
        Assertions.assertNotNull(tripsByServiceId.get("781_20211031_20211211"));
        Assertions.assertNotNull(tripsByServiceId.get("781_20201213_20210327"));
    }

    @Test
    @Transactional
    public void train9924ShouldBeOkay() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2019, 12, 18));

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_9924.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 1);
        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        Assertions.assertNotNull(tripsByServiceId.get("9924_20191215_20201212"));
    }

    @Test
    @Transactional
    public void train66ShouldBeOkay() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2019, 12, 2));

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_66.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        Trip normalTrip = tripsByServiceId.get("66_20191125_20191214");
        Trip KAJTrip = tripsByServiceId.get("66_20191201_20191201_replacement");
        Trip KUOTrip = tripsByServiceId.get("66_20191202_20191202_replacement");

        Assertions.assertEquals(normalTrip.stopTimes.get(0).stopId, "OL");
        Assertions.assertEquals(KAJTrip.stopTimes.get(0).stopId, "KAJ");
        Assertions.assertEquals(KUOTrip.stopTimes.get(0).stopId, "KUO");

        Assertions.assertEquals(Iterables.getLast(normalTrip.stopTimes).stopId, "HKI");
        Assertions.assertEquals(Iterables.getLast(KAJTrip.stopTimes).stopId, "HKI");
        Assertions.assertEquals(Iterables.getLast(KUOTrip.stopTimes).stopId, "HKI");

    }

    @Test
    @Transactional
    public void train66StopTimesHaveCorrectPlatforms() throws IOException {
        LocalDate startDate = LocalDate.of(2019, 1, 1);
        given(dp.dateInHelsinki()).willReturn(startDate);

        final List<SimpleTimeTableRow> timeTableRows = testDataService.parseEntityList(timetablerows_66.getFile(), SimpleTimeTableRow[].class);
        given(timeTableRowService.getNextTenDays()).willReturn(timeTableRows);

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_66.getFile(), Schedule[].class);

        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        final Map<Long, List<StopTime>> stopTimesByAttapId = gtfsDto.trips.stream()
                .flatMap(trip -> trip.stopTimes.stream())
                .filter(stopTime -> stopTime.source.arrival != null && stopTime.track != null)
                .collect(Collectors.groupingBy(stopTime -> stopTime.source.arrival.id));

        timeTableRows.forEach(row -> {
            List<StopTime> matchingStopTimes = stopTimesByAttapId.getOrDefault(row.getAttapId(), Collections.emptyList());
            matchingStopTimes.forEach(stopTime ->
                    Assertions.assertEquals(row.stationShortCode + "_" + row.commercialTrack, stopTime.getStopCodeWithPlatform())
            );
        });
    }

    @Test
    @Transactional
    public void allStopTracksAreFoundInStopTimes() throws IOException {
        LocalDate startDate = LocalDate.of(2019, 1, 1);
        given(dp.dateInHelsinki()).willReturn(startDate);

        final List<SimpleTimeTableRow> timeTableRows = testDataService.parseEntityList(timetablerows_66.getFile(), SimpleTimeTableRow[].class);
        given(timeTableRowService.getNextTenDays()).willReturn(timeTableRows);

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_66.getFile(), Schedule[].class);

        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        final Set<String> tracksInStopTimes = gtfsDto.trips.stream()
                .flatMap(trip -> trip.stopTimes.stream())
                .map(stopTime -> stopTime.track != null ? stopTime.getStopCodeWithPlatform() : null)
                .collect(Collectors.toSet());

        final Set<String> tracksInStops = gtfsDto.stops.stream()
                .map(stop -> stop instanceof Platform ? stop.stopId : null)
                .collect(Collectors.toSet());

        Assertions.assertTrue(tracksInStopTimes.containsAll(tracksInStops));
    }

    @Test
    @Transactional
    public void stopTypesShouldBeCorrect() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_1.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        final Trip firstTrip = tripsByServiceId.get("1_20170902_20171209");

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

        List<StopTime> stopTimes = firstTrip.stopTimes;
        Assertions.assertEquals(stopTypes.size(), stopTimes.size());

        Assertions.assertEquals(1, stopTimes.get(0).dropoffType);
        Assertions.assertEquals(0, stopTimes.get(0).pickupType);

        Assertions.assertEquals(0, Iterables.getLast(stopTimes).dropoffType);
        Assertions.assertEquals(1, Iterables.getLast(stopTimes).pickupType);

        for (int i = 1; i < stopTimes.size() - 1; i++) {
            final StopTime stopTime = stopTimes.get(i);
            Assertions.assertEquals(stopTypes.get(stopTime.stopId).intValue(), stopTime.dropoffType, stopTime.stopId);
            Assertions.assertEquals(stopTypes.get(stopTime.stopId).intValue(), stopTime.pickupType, stopTime.stopId);
        }

    }

    @Test
    @Transactional
    public void schedulePeriodChangeShouldBeOkay() throws IOException {
        given(dp.dateInHelsinki()).willReturn(LocalDate.of(2019, 12, 9));

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_141_151.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> trips = Maps.uniqueIndex(gtfsDto.trips, s -> s.tripId);

        Assertions.assertNotNull(trips.get(String.format("%s_%s_%s", 141L, "20191202", "20191214")));
        Assertions.assertNotNull(trips.get(String.format("%s_%s_%s", 151L, "20191202", "20191213")));
        Assertions.assertNotNull(trips.get(String.format("%s_%s_%s", 151L, "20191215", "20201211")));
    }

    @Test
    @Transactional
    public void differentRouteShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_1.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);
        final Trip firstTrip = tripsByServiceId.get("1_20170902_20171209");
        final Trip secondTrip = tripsByServiceId.get("1_20171210_20180617");
        final Trip thirdTrip = tripsByServiceId.get("1_20180618_20181208");

        assertTrip(firstTrip, LocalDate.of(2017, 9, 2), LocalDate.of(2017, 12, 9), true, true, true, true, true, true, true);
        assertTrip(secondTrip, LocalDate.of(2017, 12, 10), LocalDate.of(2018, 6, 17), true, true, true, true, true, true, true);
        assertTrip(thirdTrip, LocalDate.of(2018, 6, 18), LocalDate.of(2018, 12, 8), true, true, true, true, true, true, true);

    }

    @Test
    @Transactional
    public void train9ShouldBeOkay() throws IOException {

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_9.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);
        gtfsWritingService.writeGTFSFiles(gtfsDto);
        assertTrips(gtfsDto.trips, 71);

        final ImmutableMap<String, Trip> trips = Maps.uniqueIndex(gtfsDto.trips, s -> s.tripId);

        Assertions.assertNotNull(trips.get(String.format("%s_%s_%s", 9L, "20180618", "20181208")));

        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180611")));        // ma
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180612")));        // ti
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180613")));        // ke
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180614")));        // to
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180615")));        // pe
        Assertions.assertNotNull(trips.get(String.format("%s_%2$s_%2$s_replacement", 9L, "20180616")));     // la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180617")));        // su

        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180618")));        // ma
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180619")));        // ti
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180620")));        // ke
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180621")));        // to
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180622")));        // pe
        Assertions.assertNotNull(trips.get(String.format("%s_%2$s_%2$s_replacement", 9L, "20180623")));     // la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180624")));        // su

        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180625")));        // ma
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180626")));        // ti
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180627")));        // ke
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180628")));        // to
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180629")));        // pe
        Assertions.assertNotNull(trips.get(String.format("%s_%2$s_%2$s_replacement", 9L, "20180630")));     // la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 9L, "20180701")));         // su
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

        assertTripStops(trips.get(String.format("%s_%s_%s_replacement", 27L, "20171007", "20171007")), HELSINKI_UIC, TAMPERE_UIC);
    }

    @Test
    @Transactional
    public void train20ShouldBeOkay() throws IOException {
        final List<Schedule> schedules = testDataService.parseEntityList(schedules_20.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);

        assertTrips(gtfsDto.trips, 12);

        final ImmutableMap<String, Trip> trips = Maps.uniqueIndex(gtfsDto.trips, s -> s.tripId);

        assertTripStops(trips.get(String.format("%s_%s_%s", 20L, "20170902", "20171209")), OULU_UIC,
                HELSINKI_UIC);
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20170909")), KOKKOLA_UIC,
                HELSINKI_UIC);    // 9.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20170916")), KOKKOLA_UIC,
                HELSINKI_UIC);         // 16.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20170923")), KOKKOLA_UIC,
                HELSINKI_UIC);      // 23.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20170930")), KOKKOLA_UIC,
                HELSINKI_UIC);       // 30.9. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20171007")), TAMPERE_UIC,
                HELSINKI_UIC);         // 7.10. la TPE-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20171014")), KOKKOLA_UIC,
                HELSINKI_UIC);      // 14.10. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20171021")), KOKKOLA_UIC,
                HELSINKI_UIC);       // 21.10. la KOK-HKI
        assertTripStops(trips.get(String.format("%s_%2$s_%2$s_replacement", 20L, "20171028")), KOKKOLA_UIC,
                HELSINKI_UIC);       // 28.10. la YVI-HKI
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, "20171104")));            // 4.11. la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, "20171111")));           // 11.11. la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, "20171118")));           // 18.11. la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, "20171125")));           // 25.11. la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, "20171202")));            // 2.12. la
        Assertions.assertNull(trips.get(String.format("%s_%2$s_%2$s", 20L, "20171209")));            // 9.12. la

        assertTripStops(trips.get(String.format("%s_%s_%s", 20L, "20171211", "20180616")), OULU_UIC,
                HELSINKI_UIC);
        assertTripStops(trips.get(String.format("%s_%s_%s", 20L, "20180618", "20181208")), OULU_UIC,
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
        assertRoutes(gtfsDto.routes, "PYO 263");
        assertTrips(gtfsDto.trips, 3);

        final ImmutableMap<String, Trip> tripsByServiceId = Maps.uniqueIndex(gtfsDto.trips, t -> t.serviceId);

        final Trip firstTrip = tripsByServiceId.get("263_20170902_20171209");
        assertTrip(firstTrip, LocalDate.of(2017, 9, 2), LocalDate.of(2017, 12, 9), false, false, true, false, true, true,
                false);

        Assertions.assertEquals(1, firstTrip.calendar.calendarDates.size());
        Assertions.assertEquals(2, firstTrip.calendar.calendarDates.iterator().next().exceptionType);
        Assertions.assertEquals(LocalDate.of(2017, 10, 7), firstTrip.calendar.calendarDates.iterator().next().date);

        final Trip secondTrip = tripsByServiceId.get("263_20171215_20180420");
        assertTrip(secondTrip, LocalDate.of(2017, 12, 15), LocalDate.of(2018, 4, 20), true, false, true, false, true, false, false);
        Assertions.assertEquals(35, secondTrip.calendar.calendarDates.size());
        Assertions.assertEquals(1, Collections2.filter(secondTrip.calendar.calendarDates, cd -> cd.exceptionType == 1).size());
        Assertions.assertEquals(34, Collections2.filter(secondTrip.calendar.calendarDates, cd -> cd.exceptionType == 2).size());

        final Trip thirdTrip = tripsByServiceId.get("263_20180618_20181208");
        assertTrip(thirdTrip, LocalDate.of(2018, 06, 18), LocalDate.of(2018, 12, 8), true, false, false, false, true, true, false);
        Assertions.assertEquals(0, thirdTrip.calendar.calendarDates.size());

        Assertions.assertEquals(125 - 8, firstTrip.stopTimes.size());
    }

    @Test
    @Transactional
    public void stopFileOutputIsCorrect() throws IOException {
        final List<SimpleTimeTableRow> timeTableRows = testDataService.parseEntityList(timetablerows_66.getFile(), SimpleTimeTableRow[].class);
        given(timeTableRowService.getNextTenDays()).willReturn(timeTableRows);

        final List<Schedule> schedules = testDataService.parseEntityList(schedules_66.getFile(), Schedule[].class);
        final GTFSDto gtfsDto = gtfsService.createGTFSEntity(new ArrayList<>(), schedules);
        gtfsWritingService.writeGTFSFiles(gtfsDto);

        try (InputStream stopsFile = new FileInputStream("stops.txt"))
        {
            CsvParser csvParser = new CsvParser(new CsvParserSettings());

            List<String[]> parsedRows = csvParser.parseAll(stopsFile);

            String[] headers = parsedRows.get(0);
            Map<String, Integer> gtfsFieldIndices = IntStream
                    .range(0, headers.length)
                    .boxed()
                    .collect(Collectors.toMap(i -> headers[i], i -> i));

            parsedRows.subList(1, parsedRows.size()).forEach(row -> {
                String locationType = row[gtfsFieldIndices.get("location_type")];
                String parentStation = row[gtfsFieldIndices.get("parent_station")];
                String stopId = row[gtfsFieldIndices.get("stop_id")];
                String platformCode = row[gtfsFieldIndices.get("platform_code")];
                String stopCode = row[gtfsFieldIndices.get("stop_code")];

                Assertions.assertNotNull(locationType);

                if (locationType.equals(String.valueOf(LOCATION_TYPE_STATION))) {
                    // stations (location_type=1) should not have a parent station
                    Assertions.assertNull(parentStation);

                    Assertions.assertNull(platformCode);
                    Assertions.assertEquals(false, stopId.contains("_"));
                }

                if (locationType.equals(String.valueOf(LOCATION_TYPE_STOP))) {
                    // actual stops (location_type=0) should have a parent station
                    Assertions.assertNotNull(parentStation);

                    // stop_id should be of form <parent_station>_<platform_code>
                    // if platform_code is null, stop_id should be <parent_station>_0
                    if (platformCode != null) {
                        Assertions.assertEquals(parentStation + "_" + platformCode, stopId);
                    } else {
                        Assertions.assertEquals(parentStation + "_0", stopId);
                    }
                }

                Assertions.assertNull(stopCode);

            });

        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail(e.getMessage());
        }


    }

    private void assertTripStops(Trip trip, String departureStopId, String arrivalStopId) {
        Assertions.assertEquals(departureStopId, trip.stopTimes.get(0).stopId);
        Assertions.assertEquals(arrivalStopId, trip.stopTimes.get(trip.stopTimes.size() - 1).stopId);
    }

    private void assertTrip(final Trip trip, final LocalDate startDate, final LocalDate endDate, final boolean monday,
                            final boolean tuesday, final boolean wednesday, final boolean thursday, final boolean friday, final boolean saturday,
                            final boolean sunday) {
        Assertions.assertEquals(startDate, trip.calendar.startDate);
        Assertions.assertEquals(endDate, trip.calendar.endDate);
        Assertions.assertEquals(monday, trip.calendar.monday);
        Assertions.assertEquals(tuesday, trip.calendar.tuesday);
        Assertions.assertEquals(wednesday, trip.calendar.wednesday);
        Assertions.assertEquals(thursday, trip.calendar.thursday);
        Assertions.assertEquals(friday, trip.calendar.friday);
        Assertions.assertEquals(saturday, trip.calendar.saturday);
        Assertions.assertEquals(sunday, trip.calendar.sunday);
    }

    private void assertTrips(final List<Trip> trips, int expectedTripCount) {
        printTrips(trips);

        final ImmutableMap<String, Trip> tripsAreUnique = Maps.uniqueIndex(trips, s -> s.tripId);
        Assertions.assertEquals(expectedTripCount, trips.size());
    }

    private void printTrips(List<Trip> trips) {
        //System.out.println("Trips: " + Joiner.on(",").join(trips.stream().map(s -> s.tripId).sorted().collect(Collectors.toList())));
    }

    private void assertRoutes(final List<Route> routes, final String shortName) {
        Assertions.assertEquals(1, routes.size());
        final Route route = routes.iterator().next();
        Assertions.assertEquals(10, route.agencyId);
        Assertions.assertEquals(102, route.type);
        Assertions.assertEquals(shortName, route.shortName);
    }

    private void assertAgencies(final List<Agency> agencies, final int agencyId) {
        Assertions.assertEquals(1, agencies.size());
        final Agency agency = agencies.iterator().next();
        Assertions.assertEquals("VR", agency.name);
        Assertions.assertEquals(agencyId, agency.id);
        Assertions.assertEquals("Europe/Helsinki", agency.timezone);
    }

    private PlatformData getMockPlatformData() throws IOException {
        final String geometryString = "[[[506423.228795,6943376.039063],[506422.0625,6943401.15625]],[[506422.0625,6943401.15625],[506420.703125,6943426.515625],[506418.6875,6943451.84375],[506414.5625,6943502.21875]],[[506414.5625,6943502.21875],[506396.201907,6943723.197646]]]";

        ObjectMapper mapper = new ObjectMapper();
        final JsonNode geometryNode = mapper.readTree(geometryString);

        final MultiLineString geometry = infraApiPlatformService.deserializePlatformGeometry(geometryNode);

        final InfraApiPlatform SNJ_1 = new InfraApiPlatform("", "Laituri SNJ L1", "Suonenjoki laituri: 1", "1", geometry);
        final InfraApiPlatform HKI_7 = new InfraApiPlatform("", "Laituri HKI L7", "Helsinki laituri: 7", "7", geometry);
        final InfraApiPlatform MI_1 = new InfraApiPlatform("", "Laituri MI L1", "Mikkeli laituri: 1", "1", geometry);
        final InfraApiPlatform KV_1 = new InfraApiPlatform("", "Laituri KV L1", "Kouvola laituri: 1", "1", geometry);
        final InfraApiPlatform MR_2 = new InfraApiPlatform("", "Laituri MR L2", "Martinlaakso laituri: 2", "2", geometry);
        final InfraApiPlatform SKV_1 = new InfraApiPlatform("", "Laituri SKV L1", "Sukeva laituri: 1", "1", geometry);

        Map<String, List<InfraApiPlatform>> platformsByStation = Map.of(
                "SNJ", List.of(SNJ_1),
                "HKI", List.of(HKI_7),
                "MI", List.of(MI_1),
                "KV", List.of(KV_1),
                "MR", List.of(MR_2),
                "SKV", List.of(SKV_1)
                );

        return new PlatformData(platformsByStation);
    }
}
