package fi.livi.rata.avoindata.server.controller.mvc;

import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.controller.api.CompositionController;
import fi.livi.rata.avoindata.server.controller.api.LiveTrainController;
import fi.livi.rata.avoindata.server.controller.api.ScheduleController;
import fi.livi.rata.avoindata.server.controller.api.TrainController;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

public class RewriteControllerTest extends MockMvcBaseTest {
    @SpyBean
    private TrainController trainController;

    @SpyBean
    private CompositionController compositionController;

    @SpyBean
    private LiveTrainController liveTrainController;

    @SpyBean
    private ScheduleController scheduleController;

    @Test
    public void allTrainsTransformationShouldBeOkay() throws Exception {
        getJson("/all-trains?version=1");
        verify(trainController).getTrainsByVersion(eq(1L), any());

        getJson("/all-trains");
        verify(trainController).getTrainsByVersion(eq(null), any());
    }

    @Test
    public void liveTrainsTransformationShouldBeOkay() throws Exception {
        getJson("/live-trains/1?departure_date=2017-01-01&version=10");
        verify(trainController).getTrainByTrainNumberAndDepartureDate(eq(1L), eq(LocalDate.of(2017, 1, 1)), eq(10L), any());

        getJson("/live-trains/1?departure_date=2017-01-01");
        verify(trainController).getTrainByTrainNumberAndDepartureDate(eq(1L), eq(LocalDate.of(2017, 1, 1)), eq(0L), any());

        getJson("/live-trains/1");
        verify(trainController).getTrainByTrainNumberAndDepartureDate(eq(1L), eq(null), eq(0L), any());

        getJson("/live-trains?arrived_trains=1&arriving_trains=2&departed_trains=3&departing_trains=4&include_nonstopping=true&station" +
                "=HKI&version=5");
        verify(liveTrainController).getLiveTrainsUsingQuantityFiltering(eq("HKI"), eq(5L), eq(1), eq(2), eq(3), eq(4), eq(true), any());

        getJson("/live-trains?arrived_trains=1&arriving_trains=2&departed_trains=3&departing_trains=4&station=HKI&version=5");
        verify(liveTrainController).getLiveTrainsUsingQuantityFiltering(eq("HKI"), eq(5L), eq(1), eq(2), eq(3), eq(4), eq(false), any());

        getJson("/live-trains?arrived_trains=1&arriving_trains=2&departed_trains=3&departing_trains=4&station=HKI");
        verify(liveTrainController).getLiveTrainsUsingQuantityFiltering(eq("HKI"), eq(0L), eq(1), eq(2), eq(3), eq(4), eq(false), any());
        getJson("/live-trains?arrived_trains=1&station=HKI");
        verify(liveTrainController).getLiveTrainsUsingQuantityFiltering(eq("HKI"), eq(0L), eq(1), eq(5), eq(5), eq(5), eq(false), any());
        getJson("/live-trains?station=HKI");
        verify(liveTrainController).getLiveTrainsUsingQuantityFiltering(eq("HKI"), eq(0L), eq(5), eq(5), eq(5), eq(5), eq(false), any());

        getJson("/live-trains?version=1&include_nonstopping=true&station=HKI&minutes_before_departure=2&minutes_after_departure=3" +
                "&minutes_before_arrival=4&minutes_after_arrival=5");
        verify(liveTrainController).getLiveTrainsUsingTimeFiltering(eq("HKI"), eq(1L), eq(2), eq(3), eq(4), eq(5), eq(true), any());

        getJson("/live-trains?include_nonstopping=true&station=HKI&minutes_before_departure=2&minutes_after_departure=3" +
                "&minutes_before_arrival=4&minutes_after_arrival=5");
        verify(liveTrainController).getLiveTrainsUsingTimeFiltering(eq("HKI"), eq(0L), eq(2), eq(3), eq(4), eq(5), eq(true), any());

        getJson("/live-trains?station=HKI&minutes_before_departure=2&minutes_after_departure=3&minutes_before_arrival=4" +
                "&minutes_after_arrival=5");
        verify(liveTrainController).getLiveTrainsUsingTimeFiltering(eq("HKI"), eq(0L), eq(2), eq(3), eq(4), eq(5), eq(false), any());

        getJson("/live-trains?version=1");
        verify(liveTrainController).getLiveTrainsByVersion(eq(1L), any());

        getJson("/live-trains");
        verify(liveTrainController).getLiveTrainsByVersion(eq(0L), any());


    }

    @Test
    public void scheduleTransformShouldBeOkay() throws Exception {
        getJson("/schedules/1?departure_date=2017-01-01");
        verify(trainController).getTrainByTrainNumberAndDepartureDate(eq(1L), eq(LocalDate.of(2017, 1, 1)), any(Long.class), any());

        getJson("/schedules?departure_date=2017-01-01");
        verify(trainController).getTrainsByDepartureDate(eq(LocalDate.of(2017, 1, 1)), any());

        getJson("/schedules?departure_date=2017-01-01&include_nonstopping=true&limit=100&departure_station=HKI&arrival_station=TPE");
        verify(scheduleController).getTrainsFromDepartureToArrivalStation(eq("HKI"), eq("TPE"), eq(LocalDate.of(2017, 1, 1)), eq(true),
                eq(null), eq(null), eq(100), any());

        getJson("/schedules?departure_date=2017-01-01&limit=100&departure_station=HKI&arrival_station=TPE");
        verify(scheduleController).getTrainsFromDepartureToArrivalStation(eq("HKI"), eq("TPE"), eq(LocalDate.of(2017, 1, 1)), eq(false),
                eq(null), eq(null), eq(100), any());

        getJson("/schedules?departure_station=HKI&arrival_station=TPE");
        verify(scheduleController).getTrainsFromDepartureToArrivalStation(eq("HKI"), eq("TPE"), eq(null), eq(false), eq(null), eq(null),
                eq(null), any());
    }

    @Test
    public void compositionTransformationShouldBeOkay() throws Exception {
        getJson("/compositions?departure_date=2017-01-01");
        verify(compositionController).getCompositionsByDepartureDate(eq(LocalDate.of(2017, 1, 1)), any());
    }

    @Test
    public void historyTransformationShouldBeOkay() throws Exception {
        getJson("/history/1?departure_date=2017-01-01");
        verify(trainController).getTrainByTrainNumberAndDepartureDate(eq(1L), eq(LocalDate.of(2017, 1, 1)), any(Long.class), any());

        getJson("/history?departure_date=2017-01-01");
        verify(trainController).getTrainsByDepartureDate(eq(LocalDate.of(2017, 1, 1)), any());
    }

}
