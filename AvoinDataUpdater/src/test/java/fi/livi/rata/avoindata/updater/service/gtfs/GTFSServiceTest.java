package fi.livi.rata.avoindata.updater.service.gtfs;

import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiDataset;
import fi.livi.rata.avoindata.updater.service.infraapi.InfraApiMapResult;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Agency;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Route;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Shape;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Translation;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRow;
import fi.livi.rata.avoindata.updater.service.timetable.entities.ScheduleRowPart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STATION;
import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STOP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GTFSServiceTest {
    @Test
    void createGtfsFiltersTripsWithLessThanTwoStopsAfterNonStopFiltering() throws IOException {
        final GTFSEntityService gtfsEntityService = mock(GTFSEntityService.class);
        final GTFSWritingService gtfsWritingService = mock(GTFSWritingService.class);
        final ScheduleProviderService scheduleProviderService = mock(ScheduleProviderService.class);
        final LastUpdateService lastUpdateService = mock(LastUpdateService.class);
        final GTFSTripService gtfsTripService = mock(GTFSTripService.class);
        final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService = mock(TrakediaLiikennepaikkaService.class);

        final GTFSService gtfsService = new GTFSService(
                gtfsEntityService,
                gtfsWritingService,
                scheduleProviderService,
                lastUpdateService,
                gtfsTripService,
                trakediaLiikennepaikkaService);

        final GTFSDto gtfsDto = new GTFSDto();
        gtfsDto.trips = new ArrayList<>(List.of(
                createTrip("route-removed", 1001, List.of(
                        createLongStop("AAA", 1),
                        createShortStop("BBB", 2)
                )),
                createTrip("route-kept", 2002, List.of(
                        createLongStop("BBB", 3),
                        createLongStop("CCC", 4)
                ))
        ));
        gtfsDto.routes = new ArrayList<>(List.of(
                createRoute("route-removed", 10),
                createRoute("route-kept", 20)
        ));
        gtfsDto.agencies = new ArrayList<>(List.of(
                createAgency(10),
                createAgency(20)
        ));
        gtfsDto.shapes = new ArrayList<>(List.of(
                createShape(1001),
                createShape(2002)
        ));
        gtfsDto.stops = new ArrayList<>(List.of(
                createStop("AAA", "Alpha"),
                createStop("BBB", "Bravo"),
                createStop("CCC", "Charlie")
        ));
        gtfsDto.translations = new ArrayList<>(List.of(new Translation("Old", "en", "Old")));

        when(gtfsEntityService.createGTFSEntity(anyList(), anyList(), anyMap(), any(java.time.LocalDate.class),
                any(FailedSegments.class)))
                .thenReturn(gtfsDto);

        // createGtfs resolves and validates the node snapshot before building the feed.
        mockNodes(trakediaLiikennepaikkaService);

        final Schedule regularSchedule = new Schedule();
        final GTFSDto result = gtfsService.createGtfs(Collections.emptyList(), List.of(regularSchedule), "gtfs-test.zip", true);

        assertEquals(1, result.trips.size());
        assertEquals(List.of("BBB", "CCC"), result.trips.getFirst().stopTimes.stream().map(stopTime -> stopTime.stopId).toList());

        assertEquals(2, result.routes.size());
        assertEquals(2, result.agencies.size());
        assertEquals(2, result.shapes.size());

        verify(gtfsWritingService).writeGTFSFiles(result, "gtfs-test.zip");
        assertSame(gtfsDto, result);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void createGtfsRemovesStopsWithoutStopTimes(final boolean filterOutNonStopsAndMuseumTrains) throws IOException {
        final GTFSEntityService gtfsEntityService = mock(GTFSEntityService.class);
        final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService = mock(TrakediaLiikennepaikkaService.class);
        final GTFSService gtfsService = new GTFSService(
                gtfsEntityService,
                mock(GTFSWritingService.class),
                mock(ScheduleProviderService.class),
                mock(LastUpdateService.class),
                mock(GTFSTripService.class),
                trakediaLiikennepaikkaService);

        final StopTime alphaOnTrackOne = createLongStop("AAA", 1);
        alphaOnTrackOne.track = "1";

        final GTFSDto gtfsDto = new GTFSDto();
        gtfsDto.trips = new ArrayList<>(List.of(
                createTrip("route", 1001, List.of(alphaOnTrackOne, createLongStop("BBB", 2)))
        ));
        // CCC has passenger traffic but no train in this feed stops there
        gtfsDto.stops = new ArrayList<>(List.of(
                createStop("AAA", "Alpha", LOCATION_TYPE_STATION, false),
                createStop("AAA_0", "Alpha", LOCATION_TYPE_STOP, false),
                createStop("AAA_1", "Alpha", LOCATION_TYPE_STOP, false),
                createStop("AAA_2", "Alpha", LOCATION_TYPE_STOP, false),
                createStop("BBB", "Bravo", LOCATION_TYPE_STATION, false),
                createStop("BBB_0", "Bravo", LOCATION_TYPE_STOP, false),
                createStop("CCC", "Charlie", LOCATION_TYPE_STATION, true),
                createStop("CCC_0", "Charlie", LOCATION_TYPE_STOP, true),
                createStop("CCC_1", "Charlie", LOCATION_TYPE_STOP, true)
        ));
        gtfsDto.translations = new ArrayList<>(List.of(
                new Translation("Alpha", "en", "Alpha EN"),
                new Translation("Charlie", "en", "Charlie EN")
        ));

        when(gtfsEntityService.createGTFSEntity(anyList(), anyList(), anyMap(), any(java.time.LocalDate.class),
                any(FailedSegments.class)))
                .thenReturn(gtfsDto);
        mockNodes(trakediaLiikennepaikkaService);

        final GTFSDto result = gtfsService.createGtfs(Collections.emptyList(), List.of(new Schedule()), "gtfs-test.zip",
                filterOutNonStopsAndMuseumTrains);

        assertEquals(Set.of("AAA", "AAA_1", "BBB", "BBB_0"),
                result.stops.stream().map(stop -> stop.stopId).collect(Collectors.toSet()));
        assertEquals(List.of("Alpha"), result.translations.stream().map(Translation::finnishName).toList());

        final Set<String> stopIds = result.stops.stream().map(stop -> stop.stopId).collect(Collectors.toSet());
        result.trips.stream()
                .flatMap(trip -> trip.stopTimes.stream())
                .forEach(stopTime -> assertTrue(stopIds.contains(stopTime.getStopCodeWithPlatform())));
    }

    private static void mockNodes(final TrakediaLiikennepaikkaService trakediaLiikennepaikkaService) {
        final Map<InfraApiDataset, Integer> sourceCounts = new EnumMap<>(InfraApiDataset.class);
        sourceCounts.put(InfraApiDataset.RAUTATIELIIKENNEPAIKAT, 1);
        sourceCounts.put(InfraApiDataset.LIIKENNEPAIKANOSAT, 1);
        when(trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes())
                .thenReturn(InfraApiMapResult.success(Map.of(), sourceCounts, Instant.now()));
    }

    private static Stop createStop(final String stopId, final String name, final int locationType,
                                   final boolean passengerTraffic) {
        final Stop stop = createStop(stopId, name);
        stop.locationType = locationType;
        stop.source.passengerTraffic = passengerTraffic;
        return stop;
    }

    private static Agency createAgency(final int agencyId) {
        final Agency agency = new Agency();
        agency.id = agencyId;
        return agency;
    }

    private static Route createRoute(final String routeId, final int agencyId) {
        final Route route = new Route();
        route.routeId = routeId;
        route.agencyId = agencyId;
        return route;
    }

    private static Shape createShape(final int shapeId) {
        final Shape shape = new Shape();
        shape.shapeId = shapeId;
        return shape;
    }

    private static Stop createStop(final String stopId, final String name) {
        final Station station = new Station();
        station.shortCode = stopId;
        station.name = name;
        station.passengerTraffic = false;

        final Stop stop = new Stop(station);
        stop.stopId = stopId;
        stop.stopCode = stopId;
        stop.name = name;
        return stop;
    }

    private static Trip createTrip(final String routeId, final int shapeId, final List<StopTime> stopTimes) {
        final Trip trip = new Trip(new Schedule());
        trip.routeId = routeId;
        trip.shapeId = shapeId;
        trip.stopTimes = new ArrayList<>(stopTimes);
        return trip;
    }

    private static StopTime createLongStop(final String stopId, final long rowId) {
        final ScheduleRow scheduleRow = new ScheduleRow();
        scheduleRow.id = rowId;
        scheduleRow.station = new StationEmbeddable(stopId, (int) rowId, "FI");

        final ScheduleRowPart departure = new ScheduleRowPart();
        departure.id = rowId * 10;
        departure.timestamp = Duration.ofHours(rowId);
        departure.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        scheduleRow.departure = departure;

        final StopTime stopTime = new StopTime(scheduleRow);
        stopTime.stopId = stopId;
        stopTime.arrivalTime = departure.timestamp;
        stopTime.departureTime = departure.timestamp;
        return stopTime;
    }

    private static StopTime createShortStop(final String stopId, final long rowId) {
        final ScheduleRow scheduleRow = new ScheduleRow();
        scheduleRow.id = rowId;
        scheduleRow.station = new StationEmbeddable(stopId, (int) rowId, "FI");

        final ScheduleRowPart arrival = new ScheduleRowPart();
        arrival.id = rowId * 10;
        arrival.timestamp = Duration.ofHours(rowId);
        arrival.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        scheduleRow.arrival = arrival;

        final ScheduleRowPart departure = new ScheduleRowPart();
        departure.id = rowId * 10 + 1;
        departure.timestamp = Duration.ofHours(rowId);
        departure.stopType = ScheduleRow.ScheduleRowStopType.COMMERCIAL;
        scheduleRow.departure = departure;

        final StopTime stopTime = new StopTime(scheduleRow);
        stopTime.stopId = stopId;
        stopTime.arrivalTime = arrival.timestamp;
        stopTime.departureTime = departure.timestamp;
        return stopTime;
    }
}


