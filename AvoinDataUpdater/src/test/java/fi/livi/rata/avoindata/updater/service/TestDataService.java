package fi.livi.rata.avoindata.updater.service;

import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.mockito.internal.util.MockUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import fi.finrail.koju.model.KokoonpanoDto;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.config.HttpInputObjectMapper;
import fi.livi.rata.avoindata.updater.deserializers.JourneyCompositionConverter;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;

@Service
@Transactional
public class TestDataService {
    private static final Logger log = LoggerFactory.getLogger(TestDataService.class);

    @Autowired
    private CompositionService compositionService;

    @Autowired
    private TrainPersistService trainService;

    @Autowired
    private HttpInputObjectMapper objectMapper;

    @Autowired
    private TrackWorkNotificationRepository trackWorkNotificationRepository;

    @Autowired
    private TrafficRestrictionNotificationRepository trafficRestrictionNotificationRepository;

    @Autowired
    private JourneyCompositionConverter journeyCompositionConverter;

    public void mockGetTrakediaLiikennepaikkaNodes(final TrakediaLiikennepaikkaService trakediaLiikennepaikkaServiceMock) {
        Assertions.assertTrue(MockUtil.isMock(trakediaLiikennepaikkaServiceMock),
                "trakediaLiikennepaikkaServiceMock parameter must be mocked TrakediaLiikennepaikkaService service");
        final Map<String, JsonNode> liikennepaikkaMap = new HashMap<>();
        try {
            final JsonNode jsonNode = getTrakediaLocations();

            for (final JsonNode node : jsonNode) {
                final JsonNode lyhenne = node.get(0).get("lyhenne");
                liikennepaikkaMap.put(lyhenne.asText().toUpperCase(), node);
            }
        } catch (final Exception e) {
            log.error("could not fetch Trakedia data", e);
        }

        when(trakediaLiikennepaikkaServiceMock.getTrakediaLiikennepaikkaNodes()).thenReturn(liikennepaikkaMap);
    }

    @Modifying
    public void createSingleTrainComposition() throws IOException {
        compositionService.addCompositions(deserializeSingleTrainJourneyCompositions().getLeft());
    }

    @Modifying
    public void createOvernightComposition() throws IOException {
        compositionService.addCompositions(deserializeOvernightJourneyCompositions().getLeft());
    }

    public Pair<List<JourneyComposition>, List<KokoonpanoDto>> getNewestJourneyCompositions(final String resourcePath) throws IOException {
        final KokoonpanoDto[] kokoonpanot = readJourneyCompositions(resourcePath);
        final ArrayList<KokoonpanoDto> newestVersions = journeyCompositionConverter.filterNewestVersions(new ArrayList<>(Arrays.asList(kokoonpanot)));
        return journeyCompositionConverter.transformToJourneyCompositions(newestVersions);
    }

    public KokoonpanoDto[] readJourneyCompositions(final String resourcePath) throws IOException {
        return objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), KokoonpanoDto[].class);
    }

    public Pair<List<JourneyComposition>, List<KokoonpanoDto>> deserializeSingleTrainJourneyCompositions() throws IOException {
        return getNewestJourneyCompositions("/koju/julkisetkokoonpanot/2024-11-13--9715.json");
    }

    public Pair<List<JourneyComposition>, List<KokoonpanoDto>> deserializeOvernightJourneyCompositions() throws IOException {
        return getNewestJourneyCompositions("/koju/julkisetkokoonpanot/2024-11-13--265-overnight.json");
    }

    public JsonNode getTrakediaLocations() throws IOException {
        return objectMapper.readTree(new ClassPathResource("/trakedia/liikennepaikanosat.json").getFile());
    }

    public void clearTrackWorkNotifications() {
        trackWorkNotificationRepository.deleteAllInBatch();
    }

    public void clearTrafficRestrictionNotifications() {
        trafficRestrictionNotificationRepository.deleteAllInBatch();
    }

    @Modifying
    public void createTrainsFromResource(final String resourcePath) throws IOException {
        trainService.updateEntities(parseTrains(resourcePath));
    }

    public List<Train> parseTrains(final String resourcePath) throws IOException {
        return Arrays.asList(objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), Train[].class));
    }

    public <E> List<E> parseEntityList(final String resourcePath, final Class<E[]> entityClass) throws IOException {
        return Arrays.asList(objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), entityClass));
    }

    public <E> List<E> parseEntityList(final File file, final Class<E[]> entityClass) throws IOException {
        return Arrays.asList(objectMapper.readValue(file, entityClass));
    }

    public <E> E parseEntity(final String resourcePath, final Class<E> entityClass) throws IOException {
        return objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), entityClass);
    }

    public List<TrainRunningMessage> createTrainRunningMessages(final String path) throws IOException {
        final TrainRunningMessage[] trainRunningMessages = objectMapper.readValue(new ClassPathResource(path).getFile(),
                TrainRunningMessage[].class);
        return Arrays.asList(trainRunningMessages);
    }
}
