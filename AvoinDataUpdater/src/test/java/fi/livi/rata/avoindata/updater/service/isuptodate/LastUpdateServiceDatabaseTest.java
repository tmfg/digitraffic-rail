package fi.livi.rata.avoindata.updater.service.isuptodate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.common.dao.LastUpdateRepository;
import fi.livi.rata.avoindata.updater.BaseTest;

public class LastUpdateServiceDatabaseTest extends BaseTest {

    @Autowired
    private LastUpdateService lastUpdateService;

    @Autowired
    private LastUpdateRepository lastUpdateRepository;

    @Test
    public void dailyTypeIsStoredInDatabase() {
        lastUpdateService.update(LastUpdateService.LastUpdatedType.STATIONS);

        final var stored = lastUpdateRepository.findById("STATIONS").orElseThrow();
        assertEquals(lastUpdateService.getLastUpdateTimes().get(LastUpdateService.LastUpdatedType.STATIONS).toEpochMilli(),
                stored.lastUpdated.toEpochMilli());
        assertTrue(lastUpdateService.getLastUpdateTimes().containsKey(LastUpdateService.LastUpdatedType.TRAINS));
    }
}
