package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.updater.config.HttpInputObjectMapper;
import fi.livi.rata.avoindata.updater.updaters.abstractup.persist.TrainPersistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
public class TestDataService {

    @Autowired
    private CompositionService compositionService;

    @Autowired
    private TrainPersistService trainService;

    @Autowired
    private HttpInputObjectMapper objectMapper;

    @Modifying
    public void createCompositions() throws IOException {
        createCompositionsFromResourcePath("compositions.json");
    }

    @Modifying
    public void createCompositionsFromResourcePath(final String resourcePath) throws IOException {
        compositionService.addCompositions(getJourneyCompositions(resourcePath));
    }

    @Modifying
    public void createSingleTrainComposition() throws IOException {
        compositionService.addCompositions(getSingleTrainJourneyCompositions());
    }

    @Modifying
    public void createOvernightComposition() throws IOException {
        compositionService.addCompositions(getOvernightJourneyCompositions());
    }

    public List<JourneyComposition> getJourneyCompositions(final String resourcePath) throws IOException {
        return Arrays.asList(objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), JourneyComposition[].class));
    }

    public List<JourneyComposition> getSingleTrainJourneyCompositions() throws IOException {
        return getJourneyCompositions("compositionsSingleTrain.json");
    }

    public List<JourneyComposition> getOvernightJourneyCompositions() throws IOException {
        return getJourneyCompositions("compositionOvernight.json");
    }

    public JourneyComposition[] getJourneyComposition() throws IOException {
        return objectMapper.readValue(new ClassPathResource("journeyComposition.json").getFile(), JourneyComposition[].class);
    }

    public void clearCompositions() {
        compositionService.clearCompositions();
    }

    @Modifying
    public void createTrains() throws IOException {
        createTrainsFromResource("trains.json");
    }

    @Modifying
    public void createTrainsFromResource(final String resourcePath) throws IOException {
        trainService.updateEntities(parseTrains(resourcePath));
    }

    public List<Train> parseTrains(final String resourcePath) throws IOException {
        return Arrays.asList(objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), Train[].class));
    }

    public <E> List<E> parseEntityList(final String resourcePath,Class<E[]> entityClass) throws IOException {
        return Arrays.asList(objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), entityClass));
    }

    public <E> List<E> parseEntityList(final File file,Class<E[]> entityClass) throws IOException {
        return Arrays.asList(objectMapper.readValue(file, entityClass));
    }

    public <E> E parseEntity(final String resourcePath,Class<E> entityClass) throws IOException {
        return objectMapper.readValue(new ClassPathResource(resourcePath).getFile(), entityClass);
    }

    public List<TrainRunningMessage> createTrainRunningMessages(final String path) throws IOException {
        final TrainRunningMessage[] trainRunningMessages = objectMapper.readValue(new ClassPathResource(path).getFile(),
                TrainRunningMessage[].class);
        return Arrays.asList(trainRunningMessages);
    }
}
