package fi.livi.rata.avoindata.updater.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.composition.CompositionTimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.composition.JourneySectionRepository;
import fi.livi.rata.avoindata.common.dao.composition.LocomotiveRepository;
import fi.livi.rata.avoindata.common.dao.composition.WagonRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import jakarta.persistence.EntityManager;

@Transactional
public class CompositionServiceIntegrationTest extends BaseTest {

    @Autowired
    private CompositionService compositionService;

    @Autowired
    private CompositionRepository compositionRepository;

    @Autowired
    private JourneySectionRepository journeySectionRepository;

    @Autowired
    private CompositionTimeTableRowRepository compositionTimeTableRowRepository;

    @Autowired
    private LocomotiveRepository locomotiveRepository;

    @Autowired
    private WagonRepository wagonRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    @Sql({ "/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--9715.sql" })
    @Test
    public void journeySectionsShouldBeOrderedByScheduleTest() throws Exception {
        testDataService.mockGetTrakediaLiikennepaikkaNodes(trakediaLiikennepaikkaService);
        testDataService.createSingleTrainComposition();
        final Composition composition = compositionRepository.findAll().getFirst();

        final List<JourneySection> journeySections = new ArrayList<>(composition.journeySections);

        journeySections.sort(Comparator.comparing(o -> o.beginTimeTableRow.scheduledTime));

        assertThat("Journey sections should be ordered according to scheduled time",
                   composition.journeySections, contains(journeySections.toArray()));
    }

    @Sql({ "/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--265-overnight.sql" })
    @Test
    public void overnightJourneySectionsShouldBeCorrectlyOrdered() throws Exception {
        testDataService.createOvernightComposition();
        final Composition composition = compositionRepository.findAll().getFirst();

        final List<JourneySection> journeySections = new ArrayList<>(composition.journeySections);
        assertThat("Journey sections should be ordered according to scheduled time",
                journeySections.stream().map(x -> x.beginTimeTableRow.station.stationShortCode).collect(Collectors.toList()),
                contains("HKI", "PSLT", "TPE", "ROI"));
        assertEquals(journeySections.getLast().beginTimeTableRow.scheduledTime.toLocalDate(),
                composition.id.departureDate.plusDays(1),
                "Date of last section should be next day from departure");
    }

    @Sql({ "/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--265-overnight.sql" })
    @Test
    public void compositionJourneySectionsShouldHaveValidLastTimeTableRows() throws Exception {
        testDataService.createOvernightComposition();
        final Composition composition = compositionRepository.findAll().getFirst();

        final List<JourneySection> journeySections = new ArrayList<>(composition.journeySections);
        journeySections.forEach(x -> assertNotNull(x.endTimeTableRow, "endTimeTableRow must not be null"));
        for (int i = 0; i < journeySections.size(); i++) {
            final JourneySection journeySection = journeySections.get(i);
            assertEquals(TimeTableRow.TimeTableRowType.DEPARTURE, journeySection.beginTimeTableRow.type,
                    "beginTimeTableRow in JourneySection should be a departure");
            assertEquals(TimeTableRow.TimeTableRowType.ARRIVAL, journeySection.endTimeTableRow.type,
                    "endTimeTableRow in JourneySection should be an arrival");
            if (i < journeySections.size() - 1) {
                final StationEmbeddable currentStation = journeySection.endTimeTableRow.station;
                final StationEmbeddable latestStation = journeySections.get(i + 1).beginTimeTableRow.station;
                assertEquals(latestStation, currentStation,
                        "Last station in JourneySection should equal first station in the next JourneySection");
            }
        }
    }

    @Sql({ "/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--265-overnight.sql" })
    @Test
    public void testRemoveCompositionsCascadesToChildren() throws Exception {
        testDataService.createOvernightComposition();

        final int AMOUNT_OF_COMPOSITIONS = 1;

        assertEquals(AMOUNT_OF_COMPOSITIONS, compositionService.findCompositions().size(),
                String.format("Should contain %d individual compositions", AMOUNT_OF_COMPOSITIONS));

        compositionService.clearCompositions();

        assertEquals(0, compositionService.findCompositions().size(), "Databse should contain no compositions");
        assertEquals(0, journeySectionRepository.count(), "Database should contain no journeysections");
        assertEquals(0, compositionTimeTableRowRepository.count(), "Database should contain no compositiontimetablerows");
        assertEquals(0, locomotiveRepository.count(), "Database should contain no locomotives");
        assertEquals(0, wagonRepository.count(), "Database should contain no wagons");
    }

    @Sql({ "/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--265-overnight.sql" })
    @Test
    public void testUpdateCompositions() throws Exception {
        final List<JourneyComposition> journeyCompositionsFirstTwo = testDataService.deserializeOvernightJourneyCompositions().getLeft().subList(0, 2);

        compositionService.addCompositions(journeyCompositionsFirstTwo); // Add first two journey sections
        assertEquals(1, compositionRepository.count(), "Journey compositions for a single train should create one composition");
        assertEquals(2, compositionRepository.findAll().getFirst().journeySections.size(), "Composition should have first two journey sections");

        final List<JourneyComposition> journeyCompositionsAll = testDataService.deserializeOvernightJourneyCompositions().getLeft();

        entityManager.flush();
        entityManager.clear();
        compositionService.updateCompositions(journeyCompositionsAll); // Add the rest
        assertEquals(1, compositionRepository.count(), "Journey compositions for a single train should create one composition");
        assertEquals(4, compositionRepository.findAll().getFirst().journeySections.size(), "Composition should have all four journey sections");

        final LocalDate departureDate = journeyCompositionsAll.getFirst().departureDate;
        final Composition composition = compositionRepository.findById(new TrainId(265, departureDate)).orElse(null);
        assertNotNull(composition);
        assertEquals(4, composition.journeySections.size());

        compositionRepository.deleteAllInBatch();
    }

    @Test
    public void testUpdateNoUpdates() {
        compositionService.addCompositions(new ArrayList<>());
    }
}