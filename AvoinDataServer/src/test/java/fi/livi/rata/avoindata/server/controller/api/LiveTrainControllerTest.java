package fi.livi.rata.avoindata.server.controller.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.util.concurrent.MoreExecutors;

import fi.livi.rata.avoindata.common.dao.cause.CategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.CauseRepository;
import fi.livi.rata.avoindata.common.dao.cause.DetailedCategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.cause.ThirdCategoryCodeRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.localization.TrainCategory;
import fi.livi.rata.avoindata.common.domain.localization.TrainType;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.server.MockMvcBaseTest;
import fi.livi.rata.avoindata.server.controller.utils.FindByIdService;
import fi.livi.rata.avoindata.server.factory.TrainCategoryFactory;
import fi.livi.rata.avoindata.server.factory.TrainFactory;
import fi.livi.rata.avoindata.server.factory.TrainReadyFactory;
import fi.livi.rata.avoindata.server.factory.TrainTypeFactory;

public class LiveTrainControllerTest extends MockMvcBaseTest {
    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainReadyFactory trainReadyFactory;

    @Autowired
    private CauseRepository causeRepository;

    @Autowired
    private CategoryCodeRepository categoryCodeRepository;

    @Autowired
    private DetailedCategoryCodeRepository detailedCategoryCodeRepository;

    @Autowired
    private ThirdCategoryCodeRepository thirdCategoryCodeRepository;

    @Autowired
    private LiveTrainController liveTrainController;

    @Autowired
    private DateProvider dp;

    @Autowired
    private FindByIdService findByIdService;

    @Autowired
    private TrainCategoryFactory trainCategoryFactory;

    @Autowired
    private TrainTypeFactory trainTypeFactory;

    @Test
    @Transactional
    public void baseAttributesShouldBeCorrect() throws Exception {
        trainFactory.createBaseTrain();

        final ResultActions r1 = getJson("/live-trains/51");

        r1.andExpect(jsonPath("$[0].trainNumber").value("51"));
        r1.andExpect(jsonPath("$[0].departureDate").value(LocalDate.now().toString()));
        r1.andExpect(jsonPath("$[0].operatorUICCode").value("1"));
        r1.andExpect(jsonPath("$[0].operatorShortCode").value("test"));
        r1.andExpect(jsonPath("$[0].commuterLineID").value("Z"));
        r1.andExpect(jsonPath("$[0].runningCurrently").value("true"));
        r1.andExpect(jsonPath("$[0].cancelled").value("false"));
        r1.andExpect(jsonPath("$[0].version").value("1"));
    }

    @Test
    @Transactional
    public void allTimeTableRowsShouldBePresent() throws Exception {
        trainFactory.createBaseTrain();

        final ResultActions r1 = getJson("/live-trains/51");

        r1.andExpect(jsonPath("$[0].timeTableRows.length()").value(8));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].stationShortCode").value("HKI"));
        r1.andExpect(jsonPath("$[0].timeTableRows[1].stationShortCode").value("PSL"));
        r1.andExpect(jsonPath("$[0].timeTableRows[2].stationShortCode").value("PSL"));
        r1.andExpect(jsonPath("$[0].timeTableRows[3].stationShortCode").value("TPE"));
        r1.andExpect(jsonPath("$[0].timeTableRows[4].stationShortCode").value("TPE"));
        r1.andExpect(jsonPath("$[0].timeTableRows[5].stationShortCode").value("JY"));
        r1.andExpect(jsonPath("$[0].timeTableRows[6].stationShortCode").value("JY"));
        r1.andExpect(jsonPath("$[0].timeTableRows[7].stationShortCode").value("OL"));
    }

    @Test
    @Transactional
    public void trainReadyShouldBePresent() throws Exception {
        final Train train = trainFactory.createBaseTrain();

        final TimeTableRow timeTableRow = train.timeTableRows.get(0);
        trainReadyFactory.create(timeTableRow);

        final ResultActions r1 = getJson("/live-trains/51");

        r1.andExpect(jsonPath("$[0].timeTableRows[0].trainReady").exists());
        r1.andExpect(jsonPath("$[0].timeTableRows[0].trainReady.source").value("PHONE"));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].trainReady.accepted").value("true"));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].trainReady.timestamp").exists());

        r1.andExpect(jsonPath("$[0].timeTableRows[0].trainReadies").doesNotExist());
    }


    @Test
    @Transactional
    public void correctDepartureDateShouldBeSelected() throws Exception {
        trainFactory.createBaseTrain(new TrainId(51L, LocalDate.now().minusDays(1)));
        trainFactory.createBaseTrain(new TrainId(51L, LocalDate.now()));
        trainFactory.createBaseTrain(new TrainId(51L, LocalDate.now().plusDays(1)));

        final ResultActions r1 = getJson("/live-trains/51?departure_date=" + LocalDate.now().minusDays(1));
        r1.andExpect(jsonPath("$[0].departureDate").value(LocalDate.now().minusDays(1).toString()));

        final ResultActions r2 = getJson("/live-trains/51?departure_date=" + LocalDate.now());
        r2.andExpect(jsonPath("$[0].departureDate").value(LocalDate.now().toString()));

        final ResultActions r3 = getJson("/live-trains/51?departure_date=" + LocalDate.now().plusDays(1));
        r3.andExpect(jsonPath("$[0].departureDate").value(LocalDate.now().plusDays(1).toString()));

        final ResultActions r4 = getJson("/live-trains/51");
        r4.andExpect(jsonPath("$[0].departureDate").value(LocalDate.now().toString()));

    }

    @Test
    @Transactional
    public void timetableTypeShouldWork() throws Exception {
        trainFactory.createBaseTrain(new TrainId(51L, LocalDate.now()));

        Train train2 = trainFactory.createBaseTrain(new TrainId(52L, LocalDate.now()));
        train2.timetableType = Train.TimetableType.ADHOC;
        trainRepository.save(train2);

        final ResultActions r1 = getJson("/live-trains/51");
        r1.andExpect(jsonPath("$[0].timetableType").value("REGULAR"));

        final ResultActions r2 = getJson("/live-trains/52");
        r2.andExpect(jsonPath("$[0].timetableType").value("ADHOC"));
    }

    @Test
    @Transactional
    public void versionLessThanShouldShow() throws Exception {
        final Train train = trainFactory.createBaseTrain();

        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.scheduledTime = dp.nowInHelsinki();
        }

        final ResultActions r1 = getJson("/live-trains?version=0");
        r1.andExpect(jsonPath("$.length()").value(1));

        final ResultActions r2 = getJson("/live-trains?version=1");
        r2.andExpect(jsonPath("$.length()").value(0));

        final ResultActions r3 = getJson("/live-trains");
        r3.andExpect(jsonPath("$.length()").value(1));
    }


    private void clearActualTimes(Train... trains) {
        for (Train train : trains) {
            for (TimeTableRow timeTableRow : train.timeTableRows) {
                timeTableRow.actualTime = null;
            }
        }
    }

    @Test
    @Transactional
    public void causeShouldShow() throws Exception {
        final Train train = trainFactory.createBaseTrain();
        final TimeTableRow timeTableRow = train.timeTableRows.get(0);

        ThirdCategoryCode thirdCategoryCode = new ThirdCategoryCode();
        thirdCategoryCode.thirdCategoryCode = "3 koodi";
        thirdCategoryCode.thirdCategoryName = "3 koodin nimi";
        thirdCategoryCode.description = "3 koodin selitys";
        thirdCategoryCode.validFrom = LocalDate.of(2017, 1, 1);
        thirdCategoryCode.oid = "1.2.246.586.8.1.21";
        DetailedCategoryCode detailedCategoryCode = new DetailedCategoryCode();
        detailedCategoryCode.detailedCategoryCode = "2 koodi";
        detailedCategoryCode.detailedCategoryName = "2 koodin nimi";
        detailedCategoryCode.validFrom = LocalDate.of(2017, 1, 2);
        detailedCategoryCode.oid = "1.2.246.586.8.1.21.2";
        CategoryCode categoryCode = new CategoryCode();
        categoryCode.categoryCode = "1 koodi";
        categoryCode.categoryName = "1 koodin nimi";
        categoryCode.validFrom = LocalDate.of(2017, 1, 3);
        categoryCode.oid = "1.2.246.586.8.1.21.2.4";

        detailedCategoryCode.categoryCode = categoryCode;
        thirdCategoryCode.detailedCategoryCode = detailedCategoryCode;

        categoryCode = categoryCodeRepository.save(categoryCode);
        detailedCategoryCode = detailedCategoryCodeRepository.save(detailedCategoryCode);
        thirdCategoryCode = thirdCategoryCodeRepository.save(thirdCategoryCode);

        final Cause cause = new Cause();
        cause.thirdCategoryCode = thirdCategoryCode;
        cause.detailedCategoryCode = detailedCategoryCode;
        cause.categoryCode = categoryCode;
        cause.timeTableRow = timeTableRow;
        timeTableRow.causes.add(cause);

        causeRepository.save(cause);

        final ResultActions r1 = getJson("/live-trains/51");

        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0]").exists());
        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0].categoryCode").value("1 koodi"));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0].categoryCodeId").value(cause.getCategoryCodeId()));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0].detailedCategoryCode").value("2 koodi"));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0].detailedCategoryCodeId").value(cause.getDetailedCategoryCodeId()));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0].thirdCategoryCode").value("3 koodi"));
        r1.andExpect(jsonPath("$[0].timeTableRows[0].causes[0].thirdCategoryCodeId").value(cause.getThirdCategoryCodeId()));

        r1.andExpect(jsonPath("$[0].timeTableRows[1].causes[0]").doesNotExist());
    }

    @Test
    @Transactional
    public void stationSearchShouldWork() throws Exception {
        trainCategoryFactory.create(1L, "test category");

        ReflectionTestUtils.setField(findByIdService, "executor", MoreExecutors.newDirectExecutorService());

        trainFactory.createBaseTrain(new TrainId(1L, LocalDate.now()));
        trainFactory.createBaseTrain(new TrainId(2L, LocalDate.now()));

        final ResultActions r1 = getJson("/live-trains?station=PSL");
        r1.andExpect(jsonPath("$.length()").value(2));

        final ResultActions r2 = getJson("/live-trains?station=ABC");
        r2.andExpect(jsonPath("$.length()").value(0));

        final ResultActions r3 = getJson(
                "/live-trains?station=PSL&minutes_before_departure=1&minutes_after_departure=1&minutes_before_arrival=1" +
                        "&minutes_after_arrival=1");
        r3.andExpect(jsonPath("$.length()").value(0));

        final ResultActions r4 = getJson(
                "/live-trains?station=PSL&minutes_before_departure=1000&minutes_after_departure=1000" +
                        "&minutes_before_arrival=1000&minutes_after_arrival=1000");
        r4.andExpect(jsonPath("$.length()").value(2));

        final ResultActions r5 = getJson(
                "/live-trains/station/PSL?minutes_before_departure=1000&minutes_after_departure=1000" +
                        "&minutes_before_arrival=1000&minutes_after_arrival=1000");
        r5.andExpect(jsonPath("$.length()").value(2));

        final ResultActions r6 = getJson(
                "/live-trains/station/PSL");
        r6.andExpect(jsonPath("$.length()").value(2));

        ReflectionTestUtils.setField(findByIdService, "executor", Executors.newFixedThreadPool(10));
    }

    @Test
    @Transactional
    @Disabled
    public void trainCategoryFilteringShouldWork() throws Exception {
        TrainCategory trainCategory1 = trainCategoryFactory.create(1L, "test category");
        TrainCategory trainCategory2 = trainCategoryFactory.create(2L, "test cat");
        TrainType trainType = trainTypeFactory.create(trainCategory1);

        ReflectionTestUtils.setField(findByIdService, "executor", MoreExecutors.newDirectExecutorService());

        Train train1 = trainFactory.createBaseTrain(new TrainId(1L, LocalDate.now()));
        Train train2 = trainFactory.createBaseTrain(new TrainId(2L, LocalDate.now()));

        clearActualTimes(train1, train2);

        train1.trainCategoryId = 1;
        train2.trainCategoryId = 2;

        final ResultActions r1 = getJson("/live-trains/station/PSL");
        r1.andExpect(jsonPath("$.length()").value(2));

        final ResultActions r2 = getJson("/live-trains/station/PSL/?minutes_before_departure=1000&minutes_after_departure=1000&minutes_before_arrival=1000&minutes_after_arrival=1000");
        r2.andExpect(jsonPath("$.length()").value(2));
        final ResultActions r3 = getJson("/live-trains/station/PSL/?minutes_before_departure=1000&minutes_after_departure=1000&minutes_before_arrival=1000&minutes_after_arrival=1000&train_categories=test category");
        r3.andExpect(jsonPath("$.length()").value(1));

        final ResultActions r4 = getJson("/live-trains/station/PSL?arrived_trains=0&arriving_trains=0&departed_trains=0&departing_trains=2&include_nonstopping=false");
        r4.andExpect(jsonPath("$.length()").value(2));

        final ResultActions r5 = getJson("/live-trains/station/PSL?arrived_trains=0&arriving_trains=0&departed_trains=0&departing_trains=2&include_nonstopping=false&train_categories=test category, test cat,ABCD");
        r5.andExpect(jsonPath("$.length()").value(2));

        final ResultActions r6 = getJson("/live-trains/station/PSL?arrived_trains=0&arriving_trains=0&departed_trains=0&departing_trains=2&include_nonstopping=false&train_categories=test category");
        r6.andExpect(jsonPath("$.length()").value(1));

        ReflectionTestUtils.setField(findByIdService, "executor", Executors.newFixedThreadPool(10));
    }

    @Test
    @Transactional
    public void deletedTrainShouldNotBeReturnedTroughLiveTrain() throws Exception {
        ReflectionTestUtils.setField(findByIdService, "executor", MoreExecutors.newDirectExecutorService());

        final Train train = trainFactory.createBaseTrain();
        for (final TimeTableRow timeTableRow : train.timeTableRows) {
            timeTableRow.scheduledTime = dp.nowInHelsinki();
        }

        getJson("/live-trains/51").andExpect(jsonPath("$.length()").value(1));
        getJson("/live-trains?station=PSL").andExpect(jsonPath("$.length()").value(1));
        getJson("/live-trains?station=PSL&minutes_before_departure=1500&minutes_after_departure=1500&minutes_before_arrival=1500" +
                "&minutes_after_arrival=1500")
                .andExpect(jsonPath("$.length()").value(1));
        getJson("/live-trains?version=0").andExpect(jsonPath("$.length()").value(1));
        getJson("/live-trains/51?departure_date=" + LocalDate.now()).andExpect(jsonPath("$.length()").value(1));

        train.deleted = true;

        getJson("/live-trains/51").andExpect(jsonPath("$.length()").value(0));
        getJson("/live-trains?station=PSL").andExpect(jsonPath("$.length()").value(0));
        getJson("/live-trains?station=PSL&minutes_before_departure=1500&minutes_after_departure=1500&minutes_before_arrival=1500" +
                "&minutes_after_arrival=1500")
                .andExpect(jsonPath("$.length()").value(0));
        getJson("/live-trains?version=0").andExpect(jsonPath("$.length()").value(0));
        getJson("/live-trains/51?departure_date=" + LocalDate.now()).andExpect(jsonPath("$.length()").value(0));

        ReflectionTestUtils.setField(findByIdService, "executor", Executors.newFixedThreadPool(10));
    }

    @Test
    @Transactional
    public void deletedTrainShouldNotBeReturnedTroughLiveTrain2() throws Exception {
        ReflectionTestUtils.setField(findByIdService, "executor", MoreExecutors.newDirectExecutorService());

        LocalDate dateNow = LocalDate.now();
        final Train train1 = trainFactory.createBaseTrain(new TrainId(1L, dateNow));
        final Train train2 = trainFactory.createBaseTrain(new TrainId(2L, dateNow));
        final Train train3 = trainFactory.createBaseTrain(new TrainId(3L, dateNow));
        final Train train4 = trainFactory.createBaseTrain(new TrainId(4L, dateNow));
        final Train train5 = trainFactory.createBaseTrain(new TrainId(5L, dateNow));

        clearActualTimes(train1, train2, train3, train4, train5);

        ZonedDateTime zonedDateTimeNow = ZonedDateTime.now();
        train1.timeTableRows.get(0).scheduledTime = zonedDateTimeNow.plusMinutes(1);
        train2.timeTableRows.get(0).scheduledTime = zonedDateTimeNow.plusMinutes(2);
        train3.timeTableRows.get(0).scheduledTime = zonedDateTimeNow.plusMinutes(3);
        train4.timeTableRows.get(0).scheduledTime = zonedDateTimeNow.plusMinutes(4);
        train5.timeTableRows.get(0).scheduledTime = zonedDateTimeNow.plusMinutes(5);

        train1.deleted = true;
        train2.deleted = true;

        final ResultActions r1 = getJson("/live-trains/station/HKI?arrived_trains=0&arriving_trains=0&departed_trains=0&departing_trains=3&include_nonstopping=false");
        r1.andExpect(jsonPath("$.length()").value(3));
        r1.andExpect(jsonPath("$[0].trainNumber").value(3));
        r1.andExpect(jsonPath("$[1].trainNumber").value(4));
        r1.andExpect(jsonPath("$[2].trainNumber").value(5));

        ReflectionTestUtils.setField(findByIdService, "executor", Executors.newFixedThreadPool(10));
    }
}
