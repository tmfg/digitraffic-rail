package fi.livi.rata.avoindata.updater.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCollection;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainSourceVersion;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.factory.TrainFactory;

@Transactional
public class TrainRepositoryTest extends BaseTest {
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 1, 15);

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainFactory trainFactory;

    private Train createTrain(final long trainNumber, final Long sourceVersion) {
        final Train train = trainFactory.createBaseTrain(new TrainId(trainNumber, DEPARTURE_DATE));
        train.sourceVersion = sourceVersion;

        return trainRepository.save(train);
    }

    @Test
    public void findSourceVersionsByDepartureDateReturnsIdAndSourceVersion() {
        createTrain(1L, 12345L);

        final List<TrainSourceVersion> result = trainRepository.findSourceVersionsByDepartureDate(DEPARTURE_DATE);

        assertThatCollection(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(new TrainId(1L, DEPARTURE_DATE));
        assertThat(result.getFirst().getSourceVersion()).isEqualTo(12345L);
    }

    @Test
    public void findSourceVersionsByDepartureDateSkipsTrainsWithoutSourceVersion() {
        createTrain(1L, 12345L);
        createTrain(2L, null);

        final List<TrainSourceVersion> result = trainRepository.findSourceVersionsByDepartureDate(DEPARTURE_DATE);

        assertThatCollection(result).hasSize(1);
        assertThat(result.getFirst().getId().trainNumber).isEqualTo(1L);
    }
}
