package fi.livi.rata.avoindata.updater.service.hack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainSourceVersion;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;

@TestPropertySource(properties = "updater.trains.numberOfPastDaysToInitialize=5")
public class OldTrainServiceTest extends BaseTest {
    private static final long TRAIN_NUMBER = 4321L;
    private static final long STORED_SOURCE_VERSION = 111L;
    private static final long PAYLOAD_SOURCE_VERSION = 222L;

    private final LocalDate departureDate = LocalDate.now().minusDays(3);

    @Autowired
    private OldTrainService oldTrainService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Autowired
    private TrainFactory trainFactory;

    @MockitoBean
    private RipaService ripaService;

    @MockitoBean
    private LastUpdateService lastUpdateService;

    @BeforeEach
    @AfterEach
    public void cleanDatabase() {
        timeTableRowRepository.deleteAllInBatch();
        trainRepository.deleteAllInBatch();
    }

    private void storeTrain() {
        final Train train = trainFactory.createBaseTrain(new TrainId(TRAIN_NUMBER, departureDate));
        train.sourceVersion = STORED_SOURCE_VERSION;
        trainRepository.save(train);
    }

    private void mockChangedTrain() {
        final Train changed = trainFactory.createUnpersistedTrain(TRAIN_NUMBER, departureDate);
        changed.version = PAYLOAD_SOURCE_VERSION;

        when(ripaService.postToRipa(anyString(), any(HashMap.class), eq(Train[].class)))
                .thenReturn(new Train[] { changed });
    }

    @Test
    public void sourceVersionIsSentToSourceSystemNotApiVersion() {
        storeTrain();
        mockChangedTrain();

        oldTrainService.updateOldTrains();

        final ArgumentCaptor<HashMap<String, Object>> captor = ArgumentCaptor.forClass(HashMap.class);
        verify(ripaService).postToRipa(eq("old-trains"), captor.capture(), eq(Train[].class));

        @SuppressWarnings("unchecked")
        final Map<Long, Long> versions = (Map<Long, Long>) captor.getValue().get("versions");

        assertEquals(STORED_SOURCE_VERSION, versions.get(TRAIN_NUMBER),
                "the version sent to the source system must be the stored source version");
    }

    @Test
    public void sourceVersionIsStampedFromPayloadBeforePersisting() {
        storeTrain();
        mockChangedTrain();

        oldTrainService.updateOldTrains();

        final List<TrainSourceVersion> stored = trainRepository.findSourceVersionsByDepartureDate(departureDate);

        assertEquals(1, stored.size(), "the updated train must still have a source version, or it is skipped from now on");
        assertEquals(PAYLOAD_SOURCE_VERSION, stored.getFirst().getSourceVersion(),
                "source version must be taken from the payload version before updateEntities overwrites it");

        final Train updated = trainRepository.findById(new TrainId(TRAIN_NUMBER, departureDate)).orElseThrow();
        assertNotEquals(PAYLOAD_SOURCE_VERSION, updated.version,
                "the API version must be assigned separately and must not be the source version");
    }
}
