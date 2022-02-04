package fi.livi.rata.avoindata.updater;

import fi.livi.rata.avoindata.updater.service.TestDataService;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "app.scheduling.enable=false")
@SpringBootTest(classes = DatabaseUpdaterApplication.class)
public abstract class BaseTest {
    @Autowired
    protected TestDataService testDataService;

}
