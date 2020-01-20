package fi.livi.rata.avoindata.updater.factory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Component
public class TrackWorkNotificationFactory {

    private static final Random random = new Random(System.nanoTime());

    @Autowired
    private TrackWorkNotificationRepository repository;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Transactional
    public List<TrackWorkNotification> createPersist(int versions) {
        List<TrackWorkNotification> twns = create(versions);
        repository.saveAll(twns);
        return twns;
    }

    public List<TrackWorkNotification> create(int versions) {
        long id = random.nextInt(99999);
        return LongStream.rangeClosed(1, versions).mapToObj(v ->
            new TrackWorkNotification(
                    new TrackWorkNotification.TrackWorkNotificationId(id, v),
                    TrackWorkNotificationState.DRAFT,
                    UUID.randomUUID().toString(),
                    ZonedDateTime.now().minusHours(random.nextInt(100)).withNano(0),
                    ZonedDateTime.now().withNano(0),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    geometryFactory.createPoint(new Coordinate(328500.3, 6822410)),
                    geometryFactory.createPoint(new Coordinate(328500.3,6822410)))
        ).collect(Collectors.toList());
    }

}
