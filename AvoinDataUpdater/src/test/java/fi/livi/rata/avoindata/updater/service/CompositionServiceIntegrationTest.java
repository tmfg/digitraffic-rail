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

public class CompositionServiceIntegrationTest extends BaseTest {
    public static final long TEST_TRAIN_NUMBER = 263;

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

    @Test
    @Transactional
    public void journeySectionsShouldBeOrderedByScheduleTest() throws Exception {
        testDataService.createSingleTrainComposition();
        final Composition composition = compositionRepository.findAll().get(0);

        final List<JourneySection> journeySections = new ArrayList<>(composition.journeySections);

        journeySections.sort(Comparator.comparing(o -> o.beginTimeTableRow.scheduledTime));

        assertThat("Journey sections should be ordered according to scheduled time",
                composition.journeySections, contains(journeySections.toArray()));
    }

    @Test
    @Transactional
    public void overnightJourneySectionsShouldBeCorrectlyOrdered() throws Exception {
        testDataService.createOvernightComposition();
        final Composition composition = compositionRepository.findAll().iterator().next();

        final List<JourneySection> journeySections = new ArrayList<>(composition.journeySections);
        assertThat("Journey sections should be ordered according to scheduled time",
                journeySections.stream().map(x -> x.beginTimeTableRow.station.stationShortCode).collect(Collectors.toList()),
                contains("ROI", "OL", "TPE", "PSLT"));
        assertEquals(journeySections.get(journeySections.size() - 1).beginTimeTableRow.scheduledTime.toLocalDate(),
                composition.id.departureDate.plusDays(1),
                "Date of last section should be next day from departure");
    }

    @Test
    @Transactional
    public void compositionJourneySectionsShouldHaveValidLastTimeTableRows() throws Exception {
        testDataService.createOvernightComposition();
        final Composition composition = compositionRepository.findAll().iterator().next();

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

    @Test
    @Transactional
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

    @Test
    public void testUpdateCompositions() throws Exception {
        final List<JourneyComposition> journeyCompositionsFirstTwo = testDataService.getSingleTrainJourneyCompositions().subList(0, 2);

        compositionService.addCompositions(journeyCompositionsFirstTwo); // Add first two journey sections
        assertEquals(1, compositionRepository.count(), "Journey compositions for a single train should create one composition");
        assertEquals(2, compositionRepository.findAll().get(0).journeySections.size(), "Composition should have first two journey sections");

        final List<JourneyComposition> journeyCompositionsAll = testDataService.getSingleTrainJourneyCompositions();

        compositionService.updateCompositions(journeyCompositionsAll); // Add the rest
        assertEquals(1, compositionRepository.count(), "Journey compositions for a single train should create one composition");
        assertEquals(4, compositionRepository.findAll().get(0).journeySections.size(), "Composition should have all four journey sections");

        final LocalDate departureDate = journeyCompositionsAll.get(0).departureDate;
        final Composition composition = compositionRepository.findById(new TrainId(TEST_TRAIN_NUMBER, departureDate)).orElse(null);
        assertEquals(4, composition.journeySections.size());

        compositionRepository.deleteAllInBatch();
    }

    @Test
    @Transactional
    public void testUpdateNoUpdates() throws Exception {
        compositionService.addCompositions(new ArrayList<>());
    }
}