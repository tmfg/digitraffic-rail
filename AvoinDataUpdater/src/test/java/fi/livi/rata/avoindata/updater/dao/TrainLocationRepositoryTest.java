package fi.livi.rata.avoindata.updater.dao;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.updater.BaseTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;

public class TrainLocationRepositoryTest  extends BaseTest {
    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Test
    public void findLatestNoResults() {
        Assertions.assertThatCollection(trainLocationRepository.findLatest(ZonedDateTime.now())).isEmpty();
    }

    @Test
    public void findLatestForPassengerTrainsNoResults() {
        Assertions.assertThatCollection(trainLocationRepository.findLatestForPassengerTrains(ZonedDateTime.now())).isEmpty();
    }

}
