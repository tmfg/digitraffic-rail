package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import fi.livi.rata.avoindata.updater.service.gtfs.djikstra.NearestPointsService;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Stop;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.timetable.ScheduleProviderService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class GtfsCachingTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final LocalDate ROUTE_DATE = LocalDate.of(2026, 9, 21);
    private static final String START_TUNNISTE = "start";
    private static final String END_TUNNISTE = "end";
    private static final double START_LON = 24;
    private static final double START_LAT = 60;
    private static final double END_LON = 25;
    private static final double END_LAT = 61;

    private AnnotationConfigApplicationContext context;
    private TrakediaRouteService routeService;
    private InfraApiPlatformService platformService;
    private GTFSService gtfsService;
    private WebClient webClient;

    @EnableCaching
    static class CachingConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(TrakediaRouteService.CACHE_NAME, InfraApiPlatformService.CACHE_NAME);
        }

        @Bean
        WebClient webClient() {
            return mock(WebClient.class, RETURNS_DEEP_STUBS);
        }

        @Bean
        NearestPointsService nearestPointsService() {
            return mock(NearestPointsService.class);
        }

        @Bean
        Wgs84ConversionService wgs84ConversionService() {
            return mock(Wgs84ConversionService.class);
        }

        @Bean
        TrakediaRouteService trakediaRouteService() {
            return new TrakediaRouteService();
        }

        @Bean
        InfraApiPlatformService infraApiPlatformService() {
            return new InfraApiPlatformService();
        }

        @Bean
        GTFSService gtfsService() {
            return new GTFSService(mock(GTFSEntityService.class), mock(GTFSWritingService.class),
                    mock(ScheduleProviderService.class), mock(LastUpdateService.class), mock(GTFSTripService.class),
                    mock(TrakediaLiikennepaikkaService.class));
        }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(CachingConfig.class);
        routeService = context.getBean(TrakediaRouteService.class);
        platformService = context.getBean(InfraApiPlatformService.class);
        gtfsService = context.getBean(GTFSService.class);
        webClient = context.getBean(WebClient.class);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void givenAResolvedRouteWhenTheSameSegmentIsRequestedThenNoSecondRequestIsMade() throws Exception {
        // Given a segment resolved by the first feed
        respondWithGeometry();
        final Optional<List<Coordinate>> first =
                routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);
        assertThat(first).isPresent();
        clearInvocations(webClient);

        // When a later feed asks for the same tunniste pair with freshly built Stop objects
        final Optional<List<Coordinate>> second =
                routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);

        // Then the key is the tunniste pair plus the date, not Stop identity
        assertThat(second).isEqualTo(first);
        verify(webClient, never()).get();
    }

    @Test
    void givenAnotherRouteDateWhenTheSameSegmentIsRequestedThenItIsFetchedAgain() throws Exception {
        // Given geometry is scoped by the time parameter
        respondWithGeometry();
        routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);
        clearInvocations(webClient);

        // When
        routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE.plusDays(1));

        // Then a different validity date is a different entry
        verify(webClient, times(1)).get();
    }

    @Test
    void givenAnUnresolvedRouteWhenTheSameSegmentIsRequestedAgainThenItIsFetchedAgain() throws Exception {
        // Given a transient HTTP 200 carrying no geometry
        respondWith("{\"geometria\":[]}");

        // When
        final Optional<List<Coordinate>> result =
                routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);
        clearInvocations(webClient);
        routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);

        // Then one empty response must not outlive the run that saw it
        assertThat(result).isEmpty();
        verify(webClient, times(1)).get();
    }

    @Test
    void givenPlatformDataWhenItIsRequestedByEveryFeedThenItIsFetchedOnce() {
        // Given the response is not scoped by time
        respondWith("[]");

        // When every feed of one run asks for platform data
        platformService.getPlatformsByLiikennepaikkaIdPart();
        platformService.getPlatformsByLiikennepaikkaIdPart();
        platformService.getPlatformsByLiikennepaikkaIdPart();

        // Then the key is constant, so only the first call reaches the Infra API
        verify(webClient, times(1)).get();
    }

    @Test
    void givenWarmCachesWhenARunFailsThenBothAreStillEvicted() throws Exception {
        // Given both caches hold the previous run's generation
        respondWithGeometry();
        routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);
        platformService.getPlatformsByLiikennepaikkaIdPart();
        clearInvocations(webClient);

        // When a run starts and fails, because unbounded caches accumulate exactly when runs fail
        assertThatThrownBy(() -> gtfsService.generateGTFS()).isInstanceOf(RuntimeException.class);

        // Then eviction happened before invocation, so nothing survives a failed run
        routeService.createRoute(startStop(), endStop(), START_TUNNISTE, END_TUNNISTE, ROUTE_DATE);
        platformService.getPlatformsByLiikennepaikkaIdPart();
        verify(webClient, times(2)).get();
    }

    private void respondWithGeometry() {
        respondWith("{\"geometria\":[[[1,1],[2,2]]]}");
        // Anchor each stop on one end of the line so the shortest-path search has a real path.
        final Wgs84ConversionService conversionService = context.getBean(Wgs84ConversionService.class);
        when(conversionService.wgs84Tolivi(START_LON, START_LAT)).thenReturn(new ProjCoordinate(1, 1));
        when(conversionService.wgs84Tolivi(END_LON, END_LAT)).thenReturn(new ProjCoordinate(2, 2));
        final NearestPointsService nearestPointsService = context.getBean(NearestPointsService.class);
        when(nearestPointsService.kClosest(anyList(), eq(new Coordinate(1, 1)), anyInt()))
                .thenReturn(List.of(new Coordinate(1, 1)));
        when(nearestPointsService.kClosest(anyList(), eq(new Coordinate(2, 2)), anyInt()))
                .thenReturn(List.of(new Coordinate(2, 2)));
    }

    private void respondWith(final String json) {
        final JsonNode body = MAPPER.readTree(json);
        when(webClient.get().uri(anyString()).retrieve().bodyToMono(JsonNode.class).block()).thenReturn(body);
        clearInvocations(webClient);
    }

    private static Stop startStop() {
        return stop("AAA", START_LON, START_LAT);
    }

    private static Stop endStop() {
        return stop("BBB", END_LON, END_LAT);
    }

    /** A new instance per call: Stop has no equals/hashCode, so identity must not reach the key. */
    private static Stop stop(final String id, final double longitude, final double latitude) {
        final Stop stop = new Stop(null);
        stop.stopId = id;
        stop.stopCode = id;
        stop.longitude = longitude;
        stop.latitude = latitude;
        return stop;
    }
}
