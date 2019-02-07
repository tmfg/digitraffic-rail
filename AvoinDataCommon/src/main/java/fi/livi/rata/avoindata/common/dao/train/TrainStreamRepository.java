package fi.livi.rata.avoindata.common.dao.train;

import fi.livi.rata.avoindata.common.dao.localization.TrainLocalizer;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.hibernate.jpa.QueryHints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.stream.Stream;

import static fi.livi.rata.avoindata.common.dao.train.TrainRepository.*;

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

    public Stream<Train> getTrainsByDepartureDate(LocalDate departureDate) {
        String jpql = BASE_TRAIN_SELECT + " where train.id.departureDate = ?1 and " + IS_NOT_DELETED +
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
