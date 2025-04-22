package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDate;
import java.util.List;

@Transactional
@Rollback
public class SectorMapTest extends BaseTest {
    @Autowired
    private TrainFactory trainFactory;

    private static final TrainId TRAIN_ID = new TrainId(1L, LocalDate.of(2000, 1, 1));

    @Test
    public void match() {
        final Train train = trainFactory.createBaseTrain(TRAIN_ID);

        final var map = new SectorMap();
        map.initialize(List.of(new SectorMap.StopSector("PSL", "1", "Sm4", true, 4, "A1")));

        final var pslRow = train.timeTableRows.get(1);
        Assertions.assertEquals("A1", map.findStopSector(pslRow, "Sm4", true, 4));
    }

    @Test
    public void noMatch() {
        final Train train = trainFactory.createBaseTrain(TRAIN_ID);

        final var map = new SectorMap();
        map.initialize(List.of(new SectorMap.StopSector("PSL", "1", "Sm4", false, 4, "A1")));

        final var pslRow = train.timeTableRows.get(1);
        Assertions.assertNull(map.findStopSector(pslRow, "Sm4", true, 4));
    }

    @Test
    public void realSectors() {
        final Train train = trainFactory.createBaseTrain(TRAIN_ID);

        final var map = new SectorMap();
        map.initialize("sectors/sectors.csv");

        final var pslRow = train.timeTableRows.get(1);
        Assertions.assertEquals("A0", map.findStopSector(pslRow, "Sm4", true, 4));

    }
}
