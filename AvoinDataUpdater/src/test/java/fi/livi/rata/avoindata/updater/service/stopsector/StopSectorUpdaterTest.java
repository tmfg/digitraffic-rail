package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.common.dao.localization.TrainTypeRepository;
import fi.livi.rata.avoindata.common.dao.stopsector.StopSectorQueueItemRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;

@Disabled("Trashes other tests")
public class StopSectorUpdaterTest extends BaseTest {
    @Autowired
    private TrainFactory trainFactory;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainTypeRepository trainTypeRepository;

    @MockitoBean
    public StopSectorQueueItemRepository stopSectorQueueItemRepository;

    private static final String TEST_TRAIN_TYPE = "IC";
    private static final TrainId TRAIN_ID = new TrainId(1L, LocalDate.of(2000, 1, 1));

    private JourneySection createJourneySection() {
        final var journeySection = new JourneySection(null, null, null, 0, 0, null, null);

        journeySection.locomotives.add(new Locomotive(1, "Sr1", "S", "1"));
        journeySection.wagons.add(new Wagon());
        journeySection.wagons.add(new Wagon());

        return journeySection;
    }

    @Test
    public void noStopSectors() {
        final Train train = trainFactory.createBaseTrain(TRAIN_ID);
        final JourneySection journeySection = createJourneySection();

        final StopSectorUpdater stopSectorUpdater = new StopSectorUpdater(trainTypeRepository);
        stopSectorUpdater.updateStopSector(train.timeTableRows.getFirst(), journeySection, true, "Test");
        Assertions.assertNull(train.timeTableRows.get(1).stopSector);
    }

    @Test
    public void noMatch() {
        final Train train = trainFactory.createBaseTrain(TRAIN_ID);
        final JourneySection journeySection = createJourneySection();

        final StopSectorUpdater stopSectorUpdater = new StopSectorUpdater(trainTypeRepository);
        stopSectorUpdater.updateStopSector(train.timeTableRows.getFirst(), journeySection, true, "Test");
        Assertions.assertNull(train.timeTableRows.get(1).stopSector);
    }

    @Sql({"/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--9715.sql"})
    @Sql(statements = {"delete from composition"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    public void matchWithComposition() throws Exception {
        final Composition composition = testDataService.createSingleTrainComposition().getFirst();
        final Train train = trainRepository.findByDepartureDateAndTrainNumber(LocalDate.of(2024, 11, 13), 9715L, false);

        final StopSectorUpdater stopSectorUpdater = new StopSectorUpdater(trainTypeRepository);
        stopSectorUpdater.updateStopSectors(train, composition, "Test");
        Assertions.assertEquals("D5", train.timeTableRows.get(1).stopSector);
    }
}
