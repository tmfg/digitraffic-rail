package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.SimpleTransactionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

public class StopSectorServiceTest extends BaseTest {
    @Autowired
    private StopSectorService stopSectorService;

    @Autowired
    private SimpleTransactionManager simpleTransactionManager;

    private Train createTrain() {
        return new Train(91621L, LocalDate.of(2025, 1, 1), 0, null, 0, 0, null, false, false, null, null, null);
    }

    @Test
    public void addOnce() throws Exception {
        simpleTransactionManager.executeInTransaction(() -> {
            stopSectorService.addTrains(List.of(createTrain()), "TEST");
            return null;
        });
    }

    @Test
    public void addTwice() throws Exception {
        simpleTransactionManager.executeInTransaction(() -> {
            stopSectorService.addTrains(List.of(createTrain()), "TEST");
            return null;
        });

        simpleTransactionManager.executeInTransaction(() -> {
            stopSectorService.addTrains(List.of(createTrain()), "TEST");
            return null;
        });
    }

    @Test
    public void twiceOnAdd() throws Exception {
        simpleTransactionManager.executeInTransaction(() -> {
            stopSectorService.addTrains(List.of(createTrain(), createTrain()), "TEST");
            return null;
        });
    }
}
