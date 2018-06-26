package fi.livi.rata.avoindata.server.controller.api.websocket;

import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.Cause;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.server.BaseTest;
import fi.livi.rata.avoindata.server.factory.TrainFactory;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class AnnouncingServiceTest extends BaseTest {

    @Autowired
    private AnnouncingService announcingService;

    @Autowired
    private TrainFactory trainFactory;

    @Test
    @Transactional
    public void jsonViewsShouldBeUsed() {
        Train train = trainFactory.createBaseTrain();
        Cause cause = new Cause();
        CategoryCode categoryCode = new CategoryCode();
        categoryCode.id = 1L;
        categoryCode.categoryCode="Koodi";
        cause.categoryCode = categoryCode;
        train.timeTableRows.get(0).causes.add(cause);

        String json = announcingService.announce("jonneki", train);

        Assert.assertTrue(json.contains("categoryCode"));
        Assert.assertTrue(json.contains("\"categoryCodeId\":1"));
    }
}