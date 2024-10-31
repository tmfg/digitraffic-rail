package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TrakediaLiikennepaikkaServiceTest extends BaseTest {
    @Autowired
    private TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    @SpyBean(name = "webClient")
    private WebClient webClient;

    @Test
    public void getTrakediaLiikennepaikkasTwice() {
        final var liikennepaikkaMap = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkas();

        // should be called three times(liikennepaikka, liikennepaikkaosa, raideosuus)
        Assertions.assertTrue(liikennepaikkaMap.size() > 100);
        verify(webClient, times(3)).get();

        // should be from cache
        trakediaLiikennepaikkaService.getTrakediaLiikennepaikkas();
        verify(webClient, times(3)).get();
    }

    @Test
    public void getTrakediaLiikennepaikkaNodes() {
        final var liikennepaikkaMap = trakediaLiikennepaikkaService.getTrakediaLiikennepaikkaNodes();

        Assertions.assertTrue(liikennepaikkaMap.size() > 100);
    }
}
