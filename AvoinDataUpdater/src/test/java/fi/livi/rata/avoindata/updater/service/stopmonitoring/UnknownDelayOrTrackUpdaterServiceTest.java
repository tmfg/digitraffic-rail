package fi.livi.rata.avoindata.updater.service.stopmonitoring;

import fi.livi.rata.avoindata.common.dao.stopmonitoring.UdotRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.domain.common.TimeTableRowId;
import fi.livi.rata.avoindata.common.domain.stopmonitoring.Udot;
import fi.livi.rata.avoindata.common.domain.stopmonitoring.UdotData;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public class UnknownDelayOrTrackUpdaterServiceTest extends BaseTest {

    @Autowired
    private UnknownDelayOrTrackUpdaterService unknownDelayOrTrackUpdaterService;

    @Autowired
    private UdotRepository udotRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final LocalDate DATE = LocalDate.of(2014, 12, 10);
    private static final int ATTAP_ID_1 = 20906355;
    private static final int ATTAP_ID_2 = 20906356;

    private void createUdot(final boolean unknownDelay, final boolean unknownTrack) {
        final Udot udot = new Udot();
        udot.id = 123L;
        udot.attapId= ATTAP_ID_1;
        udot.trainDepartureDate = DATE;
        udot.trainNumber = 10600;
        udot.unknownDelay = unknownDelay;
        udot.unknownTrack = unknownTrack;

        udotRepository.save(udot);
    }

    private void assertUnhandledCount(final int expected) {
        final List<UdotData> unhandled = udotRepository.findByModelUpdatedTimeIsNullOrderByModifiedDb();

        Assertions.assertEquals(expected, unhandled.size());
    }

    private void assertUdotInRow(final int attapId, final Boolean expectedUd, final Boolean expectedUt) {
        final Optional<TimeTableRow> row = timeTableRowRepository.findById(new TimeTableRowId(attapId, DATE, 10600));

        Assertions.assertTrue(row.isPresent());
        Assertions.assertEquals(expectedUd, row.get().unknownDelay);
        Assertions.assertEquals(expectedUt, row.get().unknownTrack);
    }

    @AfterEach
    public void afterEach() {
        udotRepository.deleteAll();
    }

    @Test
    public void handleUdotTrueTrue() throws IOException {
        assertUnhandledCount(0);

        testDataService.createTrainsFromResource("trainsSingle.json");
        createUdot(true, true);
        assertUnhandledCount(1);
        assertUdotInRow(ATTAP_ID_1, null, null);

        unknownDelayOrTrackUpdaterService.updateUdotInformation();
        assertUnhandledCount(0);
        assertUdotInRow(ATTAP_ID_1, true, true);
    }

    @Test
    public void handleUdotTrueFalse() throws IOException {
        assertUnhandledCount(0);

        testDataService.createTrainsFromResource("trainsSingle.json");
        createUdot(true, false);
        assertUnhandledCount(1);
        assertUdotInRow(ATTAP_ID_1, null, null);

        unknownDelayOrTrackUpdaterService.updateUdotInformation();
        assertUnhandledCount(0);
        assertUdotInRow(ATTAP_ID_1, true, null);
    }

    @Test
    public void delayTrueToFalse() throws IOException {
        assertUnhandledCount(0);

        testDataService.createTrainsFromResource("trainsSingle.json");
        createUdot(true, false);

        unknownDelayOrTrackUpdaterService.updateUdotInformation();
        assertUdotInRow(ATTAP_ID_1, true, null);
        assertUdotInRow(ATTAP_ID_2, true, null);

        // clear delay
        createUdot(false, false);
        unknownDelayOrTrackUpdaterService.updateUdotInformation();
        assertUdotInRow(ATTAP_ID_1, false, null);
        assertUdotInRow(ATTAP_ID_2, false, null);
    }

    // --- New tests for deadlock fix (update by id with optimistic lock) ---

    @Test
    public void projectionIncludesId() {
        // given
        createUdot(true, true);

        // when
        final List<UdotData> unhandled = udotRepository.findByModelUpdatedTimeIsNullOrderByModifiedDb();

        // then
        Assertions.assertEquals(1, unhandled.size());
        Assertions.assertNotNull(unhandled.get(0).getId());
        Assertions.assertEquals(123L, unhandled.get(0).getId());
    }

    @Test
    @Transactional
    public void setModelUpdatedById_updatesWhenModifiedDbMatches() {
        // given
        createUdot(true, true);
        final UdotData data = udotRepository.findByModelUpdatedTimeIsNullOrderByModifiedDb().get(0);

        // when
        final int updated = udotRepository.setModelUpdated(data.getId(), data.getModifiedDb());
        entityManager.flush();
        entityManager.clear();

        // then
        Assertions.assertEquals(1, updated);
        assertUnhandledCount(0);
    }
}
