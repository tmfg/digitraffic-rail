package fi.livi.rata.avoindata.updater.service.siri.sx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.dao.metadata.StationRepository;
import fi.livi.rata.avoindata.common.dao.netex.NeTExPublishedJourneyRepository;
import fi.livi.rata.avoindata.common.dao.rami.RamiFirstCreated;
import fi.livi.rata.avoindata.common.dao.rami.RamiMessageRepository;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.domain.metadata.Station;
import fi.livi.rata.avoindata.common.domain.netex.NeTExPublishedJourney;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessage.MessageType;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageStation;
import fi.livi.rata.avoindata.common.domain.rami.RamiMessageVideo;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiUicMatcher;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;

public class SiriSxGenerationServiceTest {

    private RamiMessageRepository ramiMessageRepository;
    private NeTExPublishedJourneyRepository publishedJourneyRepository;
    private GeneratedExportRepository generatedExportRepository;
    private final LastUpdateService lastUpdateService = mock(LastUpdateService.class);
    private SiriSxGenerationService service;

    @BeforeEach
    public void setUp() {
        ramiMessageRepository = mock(RamiMessageRepository.class);
        generatedExportRepository = mock(GeneratedExportRepository.class);
        final StationRepository stationRepository = mock(StationRepository.class);
        publishedJourneyRepository = mock(NeTExPublishedJourneyRepository.class);
        final PetiStopSource petiStopSource = mock(PetiStopSource.class);

        final Station station = new Station();
        station.shortCode = "HKI";
        station.uicCode = 1;
        when(stationRepository.findAll()).thenReturn(List.of(station));
        when(petiStopSource.getMatcher()).thenReturn(new PetiUicMatcher(List.of(
                new PetiStop("FSR:StopPlace:1", 1_000_001, "Helsinki", true, null, List.of()))));
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(1L);
        final NeTExPublishedJourney journey = new NeTExPublishedJourney(
                new TrainId(1L, LocalDate.now()), "FTR:ServiceJourney:1", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:1", 1L, DateProvider.nowInHelsinki());
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of(journey));

        final ZonedDateTime now = DateProvider.nowInHelsinki();
        final RamiMessage message = new RamiMessage();
        message.id = "SHM1";
        message.version = 1;
        message.messageType = MessageType.SCHEDULED_MESSAGE;
        message.createdSource = now.minusHours(1);
        message.startValidity = now.minusHours(1);
        message.endValidity = now.plusHours(1);
        final RamiMessageVideo video = new RamiMessageVideo();
        video.id = 1L;
        video.textFi = "Raide 3 suljettu";
        message.videos.add(video);
        final RamiMessageStation messageStation = new RamiMessageStation();
        messageStation.id = 1L;
        messageStation.stationShortCode = "HKI";
        message.stations.add(messageStation);
        when(ramiMessageRepository.findLatestActive(any(), any())).thenReturn(List.of(message));

        final RamiFirstCreated first = mock(RamiFirstCreated.class);
        when(first.getId()).thenReturn("SHM1");
        when(first.getCreated()).thenReturn(now.minusHours(1));
        when(ramiMessageRepository.findFirstCreated(anyCollection())).thenReturn(List.of(first));

        service = new SiriSxGenerationService(ramiMessageRepository, stationRepository, publishedJourneyRepository,
                petiStopSource, new SiriWritingService(), generatedExportRepository, lastUpdateService);
    }

    @SuppressWarnings("unchecked")
    private GeneratedExport firstStored() {
        final ArgumentCaptor<List<GeneratedExport>> captor = ArgumentCaptor.forClass(List.class);
        verify(generatedExportRepository).persist(captor.capture());
        return captor.getValue().getFirst();
    }

    @Test
    public void storesValidSxFile() {
        service.generate();

        final GeneratedExport stored = firstStored();
        assertEquals("siri-sx.xml", stored.fileName);
        final String xml = new String(stored.data, StandardCharsets.UTF_8);
        assertTrue(xml.contains("<StopPlaceRef>FSR:StopPlace:1</StopPlaceRef>"));
        assertTrue(xml.contains("<SituationNumber>FTR:SituationNumber:SHM1</SituationNumber>"));
    }

    @Test
    public void unchangedSituationsOnlyUpdateTimeOfLastFile() {
        service.generate();
        final GeneratedExport last = firstStored();
        last.created = DateProvider.nowInHelsinki().minusMinutes(10);
        final ZonedDateTime oldCreated = last.created;
        // The next run is made a bit later: only the timestamps differ
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-sx.xml")).thenReturn(last);
        org.mockito.Mockito.clearInvocations(generatedExportRepository);

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
        assertTrue(last.created.isAfter(oldCreated));
    }

    @Test
    public void noMessagesStoresValidEmptyFile() {
        when(ramiMessageRepository.findLatestActive(any(), any())).thenReturn(List.of());

        service.generate();

        final String xml = new String(firstStored().data, StandardCharsets.UTF_8);
        assertTrue(xml.contains("<SituationExchangeDelivery"));
        assertTrue(!xml.contains("<PtSituationElement"));
        verify(ramiMessageRepository, never()).findFirstCreated(any());
    }

    @Test
    public void publishedJourneysAreReadForDeparturesOfTrainMessages() {
        final LocalDate departure = LocalDate.now().plusDays(2);
        final RamiMessage trainMessage = ramiMessageRepository.findLatestActive(null, null).getFirst();
        trainMessage.id = "MVM1";
        trainMessage.messageType = MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE;
        trainMessage.trainNumber = 1;
        trainMessage.trainDepartureDate = departure;

        service.generate();

        verify(publishedJourneyRepository).findByDatasetVersionAndDepartureDatesFetchTracks(1L, List.of(departure));
    }

    private RamiMessage addTrainMessage() {
        final RamiMessage trainMessage = ramiMessageRepository.findLatestActive(null, null).getFirst();
        final RamiMessage copy = new RamiMessage();
        copy.id = "MVM1";
        copy.version = 1;
        copy.messageType = MessageType.MONITORED_JOURNEY_SCHEDULED_MESSAGE;
        copy.createdSource = trainMessage.createdSource;
        copy.startValidity = trainMessage.startValidity;
        copy.endValidity = trainMessage.endValidity;
        copy.trainNumber = 1;
        copy.trainDepartureDate = LocalDate.now();
        copy.videos.addAll(trainMessage.videos);
        when(ramiMessageRepository.findLatestActive(any(), any())).thenReturn(List.of(trainMessage, copy));
        return copy;
    }

    private String storedXml() {
        return new String(firstStored().data, StandardCharsets.UTF_8);
    }

    @Test
    public void missingJourneysDropTrainMessagesButKeepStationMessages() {
        addTrainMessage();
        when(publishedJourneyRepository.getMaxDatasetVersion()).thenReturn(null);

        service.generate();

        final String xml = storedXml();
        assertTrue(xml.contains("FTR:SituationNumber:SHM1"));
        assertTrue(!xml.contains("FTR:SituationNumber:MVM1"));
        verify(lastUpdateService).update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
        verify(lastUpdateService, org.mockito.Mockito.never()).update(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS);
    }

    @Test
    public void oldJourneysDropTrainMessagesButKeepStationMessages() {
        addTrainMessage();
        final NeTExPublishedJourney old = new NeTExPublishedJourney(
                new TrainId(1L, LocalDate.now()), "FTR:ServiceJourney:1", "FTR:Line:IC", "FTR:Operator:vr",
                "FTR:JourneyPattern:1", 1L, DateProvider.nowInHelsinki().minusHours(31));
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of(old));

        service.generate();

        final String xml = storedXml();
        assertTrue(xml.contains("FTR:SituationNumber:SHM1"));
        assertTrue(!xml.contains("FTR:SituationNumber:MVM1"));
        verify(lastUpdateService).update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
        verify(lastUpdateService, org.mockito.Mockito.never()).update(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS);
    }

    @Test
    public void noJourneysForTodayDropTrainMessagesAndTrainsAreNotUpdated() {
        addTrainMessage();
        when(publishedJourneyRepository.findByDatasetVersionAndDepartureDatesFetchTracks(
                org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(List.of());

        service.generate();

        final String xml = storedXml();
        assertTrue(xml.contains("FTR:SituationNumber:SHM1"));
        assertTrue(!xml.contains("FTR:SituationNumber:MVM1"));
        verify(lastUpdateService).update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
        verify(lastUpdateService, org.mockito.Mockito.never()).update(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS);
    }

    @Test
    public void freshJourneysGiveTrainMessages() {
        addTrainMessage();

        service.generate();

        assertTrue(storedXml().contains("FTR:SituationNumber:MVM1"));
    }

    @Test
    public void publishedJourneysAreNotReadWithoutTrainMessages() {
        service.generate();

        verify(publishedJourneyRepository, never()).getMaxDatasetVersion();
    }

    @Test
    public void changedSituationsStoreNewFile() {
        final GeneratedExport last = new GeneratedExport();
        last.data = "<Siri/>".getBytes(StandardCharsets.UTF_8);
        last.created = DateProvider.nowInHelsinki().minusMinutes(10);
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-sx.xml")).thenReturn(last);

        service.generate();

        assertNotNull(firstStored().data);
    }

    @Test
    public void successfulRunMarksSxUpdated() {
        service.generate();

        verify(lastUpdateService).update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
        verify(lastUpdateService).update(LastUpdateService.LastUpdatedType.SIRI_SX_TRAINS);
    }

    @Test
    public void unchangedRunMarksSxUpdated() {
        service.generate();
        final GeneratedExport last = firstStored();
        when(generatedExportRepository.findFirstByFileNameOrderByIdDesc("siri-sx.xml")).thenReturn(last);

        service.generate();

        verify(lastUpdateService, org.mockito.Mockito.times(2)).update(LastUpdateService.LastUpdatedType.SIRI_SX_STATIONS);
    }

    @Test
    public void failedRunStoresNothing() {
        when(ramiMessageRepository.findLatestActive(any(), any())).thenThrow(new IllegalStateException("db down"));

        service.generate();

        verify(generatedExportRepository, never()).persist(any());
    }
}
