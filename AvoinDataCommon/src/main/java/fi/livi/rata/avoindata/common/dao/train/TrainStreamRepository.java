package fi.livi.rata.avoindata.common.dao.train;

import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.BASE_TRAIN_ORDER;
import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.BASE_TRAIN_SELECT;
import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.IS_NOT_DELETED;

import java.time.LocalDate;
import java.util.Collection;
import java.util.stream.Stream;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.hibernate.jpa.QueryHints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.dao.localization.TrainLocalizer;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;

@Component
public class TrainStreamRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TrainLocalizer trainLocalizer;

    public Stream<Train> getByTrainIds(Collection<TrainId> trainIds) {
        String jpql = BASE_TRAIN_SELECT + " where train.id in (?1) and " + IS_NOT_DELETED + " " + BASE_TRAIN_ORDER;

        return getStream(jpql, trainIds);
    }

    public Stream<Train> getTrainsByDepartureDate(LocalDate departureDate, Boolean include_deleted) {
        String jpql = BASE_TRAIN_SELECT + " where train.id.departureDate = ?1 and " + (include_deleted ? "1 = 1" : IS_NOT_DELETED) +
                BASE_TRAIN_ORDER;


        return getStream(jpql, departureDate);
    }

    private Stream<Train> getStream(String jpql, Object firstParameter) {
        Stream<Train> resultStream = entityManager.createQuery(jpql, Train.class)
                .setParameter(1, firstParameter)
                .setHint(QueryHints.HINT_FETCH_SIZE, 50).getResultStream();


        return resultStream.map(train -> trainLocalizer.localize(train));
    }
}
