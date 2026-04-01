package fi.livi.rata.avoindata.updater.updaters.abstractup.initializers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.TrainPublishingService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

public class TrainInitializerServiceTest extends BaseTest {

    @Autowired
    private TrainInitializerService trainInitializerService;

    @Autowired
    private TrainPersistService trainPersistService;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TimeTableRowRepository timeTableRowRepository;

    @Autowired
    private TrainFactory trainFactory;

    @MockitoBean
    private RipaService ripaService;

    @MockitoBean
    private TrainPublishingService trainPublishingService;

    @MockitoBean
    private LastUpdateService lastUpdateService;

    @BeforeEach
    @AfterEach
    public void cleanDatabase() {
        timeTableRowRepository.deleteAllInBatch();
        trainRepository.deleteAllInBatch();
    }

    /**
     * Creates an unpersisted train whose train.version field (= the payload source version
     * as set by TrainDeserializer) is set to the given value.
     */
    private Train trainWithPayloadSourceVersion(final long trainNumber, final long payloadSourceVersion) {
        final Train train = trainFactory.createUnpersistedTrain(trainNumber, LocalDate.now().plusDays(10));
        train.version = payloadSourceVersion;
        return train;
    }

    private void mockRipaResponse(final List<Train> trains, final Long headerVersion) {
        final Train[] trainArray = trains.toArray(new Train[0]);
        when(ripaService.getFromRipaRestTemplateWithVersion(anyString(), any()))
                .thenReturn(new RipaService.ResponseWithVersion<>(trainArray, headerVersion));
        doNothing().when(trainPublishingService).publish(any());
    }

    // --- sourceVersion stamping from payload ---

    @Test
    public void sourceVersionIsSetFromPayloadNotFromHeader() {
        final long payloadSourceVersion = 5000L;
        final long headerVersion = 9999L; // header differs — sourceVersion must come from payload
        mockRipaResponse(List.of(trainWithPayloadSourceVersion(1L, payloadSourceVersion)), headerVersion);

        trainInitializerService.doUpdate();

        final Train saved = trainRepository.findAll().get(0);
        assertEquals(payloadSourceVersion, saved.sourceVersion,
                "train.sourceVersion must be set from the payload version embedded in the train data, not from the fira-data-version header");
    }

    @Test
    public void sourceVersionIsSetIndependentlyPerTrain() {
        final long payloadVersion1 = 1000L;
        final long payloadVersion2 = 2000L;
        mockRipaResponse(
                List.of(
                        trainWithPayloadSourceVersion(1L, payloadVersion1),
                        trainWithPayloadSourceVersion(2L, payloadVersion2)
                ),
                99999L
        );

        trainInitializerService.doUpdate();

        final List<Train> saved = trainRepository.findAll();
        final Train train1 = saved.stream().filter(t -> t.id.trainNumber == 1L).findFirst().orElseThrow();
        final Train train2 = saved.stream().filter(t -> t.id.trainNumber == 2L).findFirst().orElseThrow();
        assertEquals(payloadVersion1, train1.sourceVersion, "train 1 sourceVersion must come from its own payload version");
        assertEquals(payloadVersion2, train2.sourceVersion, "train 2 sourceVersion must come from its own payload version");
    }

    @Test
    public void apiVersionIsNotCorruptedBySourceVersionAssignment() {
        final long previousMaxApiVersion = trainPersistService.getMaxApiVersion();
        mockRipaResponse(List.of(trainWithPayloadSourceVersion(1L, 5000L)), 5000L);

        trainInitializerService.doUpdate();

        final Train saved = trainRepository.findAll().get(0);
        assertEquals(previousMaxApiVersion + 1, saved.version,
                "API version must be assigned sequentially, not taken from sourceVersion");
        assertNotNull(saved.sourceVersion, "sourceVersion must not be null after update");
    }

    // --- currentSourceVersion advancement from header ---

    @Test
    public void currentSourceVersionAdvancesFromHeaderWhenPresent() {
        final long headerVersion = 7777L;
        mockRipaResponse(List.of(trainWithPayloadSourceVersion(1L, 1000L)), headerVersion);

        trainInitializerService.doUpdate();

        // Verify that the next query uses the header version by capturing the path argument.
        final long[] capturedVersion = {-1L};
        when(ripaService.getFromRipaRestTemplateWithVersion(anyString(), any())).thenAnswer(inv -> {
            final String path = inv.getArgument(0);
            capturedVersion[0] = Long.parseLong(path.split("version=")[1]);
            return new RipaService.ResponseWithVersion<>(new Train[0], headerVersion);
        });

        trainInitializerService.doUpdate();

        assertEquals(headerVersion, capturedVersion[0],
                "After a successful update with a header, currentSourceVersion must be set to the header value");
    }

    @Test
    public void currentSourceVersionAdvancesFromPayloadMaxWhenHeaderAbsent() {
        final long payloadVersion1 = 3000L;
        final long payloadVersion2 = 4000L; // max
        mockRipaResponse(
                List.of(
                        trainWithPayloadSourceVersion(1L, payloadVersion1),
                        trainWithPayloadSourceVersion(2L, payloadVersion2)
                ),
                null // no header
        );

        trainInitializerService.doUpdate();

        // Verify the next query uses max payload version as the cursor.
        final long[] capturedVersion = {-1L};
        when(ripaService.getFromRipaRestTemplateWithVersion(anyString(), any())).thenAnswer(inv -> {
            final String path = inv.getArgument(0);
            capturedVersion[0] = Long.parseLong(path.split("version=")[1]);
            return new RipaService.ResponseWithVersion<>(new Train[0], null);
        });

        trainInitializerService.doUpdate();

        assertEquals(payloadVersion2, capturedVersion[0],
                "When fira-data-version header is absent, currentSourceVersion must fall back to max sourceVersion from the batch");
    }
}

