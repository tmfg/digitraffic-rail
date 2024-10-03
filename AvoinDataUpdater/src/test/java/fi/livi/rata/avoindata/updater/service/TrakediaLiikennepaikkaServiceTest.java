package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TrakediaLiikennepaikkaServiceTest extends BaseTest {
    @Autowired
    private TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    @Test
    public void getTrakediaLiikennepaikkas() {
        final var liikennepaikkaMap = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkas();

        Assertions.assertTrue(liikennepaikkaMap.size() > 100);
    }

    @Test
    public void getTrakediaLiikennepaikkaNodes() {
        final var liikennepaikkaMap = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes();

        Assertions.assertTrue(liikennepaikkaMap.size() > 100);
    }
}
