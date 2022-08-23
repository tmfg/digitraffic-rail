package fi.livi.rata.avoindata.updater;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import fi.livi.rata.avoindata.updater.service.TestDataService;

@TestPropertySource(properties = "app.scheduling.enable=false")
@SpringBootTest(classes = DatabaseUpdaterApplication.class)
public abstract class BaseTest {
    @Autowired
    protected TestDataService testDataService;

}
