package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.util.concurrent.MoreExecutors;
import fi.livi.rata.avoindata.common.domain.routeset.Routesection;
import fi.livi.rata.avoindata.common.domain.routeset.Routeset;
import fi.livi.rata.avoindata.common.utils.BatchExecutionService;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.factory.RoutesetFactory;

@Transactional
public class RoutesetControllerTest extends MockMvcBaseTest {
    @Autowired
    private RoutesetFactory routesetFactory;

    @Autowired
    private BatchExecutionService bes;

    @BeforeEach
    public void setup() throws NoSuchFieldException {
        ReflectionTestUtils.setField(bes, "executor", MoreExecutors.newDirectExecutorService());
    }

    @AfterEach
    public void teardown() throws NoSuchFieldException {
        ReflectionTestUtils.setField(bes, "executor", Executors.newFixedThreadPool(10));
    }

    @Test
    public void versionLimitingShouldWork() throws Exception {
        Routeset routeset = routesetFactory.create();
        routeset.version = 1L;

        Routeset routeset2 = routesetFactory.create();
        routeset2.version = 2L;

        assertLength("/routesets?version=0", 2);
        assertLength("/routesets?version=1", 1);
        assertLength("/routesets?version=2", 0);
    }

    @Test
    public void trainLimitingShouldWork() throws Exception {
        Routeset routeset = routesetFactory.create();

        assertLength("/routesets/2019-01-01/1", 1);
        assertLength("/routesets/2019-01-01/2", 0);
    }

    @Test
    public void stationLimitingShouldWork() throws Exception {
        Routeset routeset = routesetFactory.create();
        Routeset routeset2 = routesetFactory.create();
        for (Routesection routesection : routeset2.routesections) {
            routesection.stationCode = "STA2";
        }
        Routeset routeset3 = routesetFactory.create();
        for (Routesection routesection : routeset3.routesections) {
            routesection.stationCode = "ABC";
        }


        assertLength("/routesets/station/STA/2019-01-02", 0);
        assertLength("/routesets/station/STA/2019-01-01", 1);
        assertLength("/routesets/station/STA2/2019-01-01", 1);
        assertLength("/routesets/station/ABC/2019-01-01", 1);
    }

    @Test
    public void nullDepartureDateShouldBeOkay() throws Exception {
        Routeset routeset = routesetFactory.create();
        routeset.trainId.departureDate = null;

        ResultActions json = getJson("/routesets/2019-01-01/1");

        json.andExpect(jsonPath("$.length()").value(1));

        json.andExpect(jsonPath("$[0].trainNumber").value(1));
        json.andExpect(jsonPath("$[0].departureDate").doesNotExist());
    }

    @Test
    public void formatShouldBeOkay() throws Exception {
        routesetFactory.create();

        ResultActions json = getJson("/routesets/2019-01-01/1");

        json.andExpect(jsonPath("$[0].trainNumber").value(1));
        json.andExpect(jsonPath("$[0].departureDate").value("2019-01-01"));
        json.andExpect(jsonPath("$[0].routeType").value("T"));
        json.andExpect(jsonPath("$[0].clientSystem").value("TEST_C"));
        json.andExpect(jsonPath("$[0].messageId").value("123"));

        json.andExpect(jsonPath("$[0].routesections.length()").value("4"));

        json.andExpect(jsonPath("$[0].routesections[0].stationCode").value("STA"));
        json.andExpect(jsonPath("$[0].routesections[0].commercialTrackId").value("TC_1"));
        json.andExpect(jsonPath("$[0].routesections[0].sectionId").value("STA_TC_1"));

        json.andExpect(jsonPath("$[0].routesections[1].stationCode").value("STA"));
        json.andExpect(jsonPath("$[0].routesections[1].commercialTrackId").value("TC_2"));
        json.andExpect(jsonPath("$[0].routesections[1].sectionId").value("STA_TC_2"));

        json.andExpect(jsonPath("$[0].routesections[2].stationCode").value("STA"));
        json.andExpect(jsonPath("$[0].routesections[2].commercialTrackId").value("TC_3"));
        json.andExpect(jsonPath("$[0].routesections[2].sectionId").value("STA_TC_3"));

        json.andExpect(jsonPath("$[0].routesections[3].stationCode").value("STA"));
        json.andExpect(jsonPath("$[0].routesections[3].commercialTrackId").value("TC_4"));
        json.andExpect(jsonPath("$[0].routesections[3].sectionId").value("STA_TC_4"));
    }
}
