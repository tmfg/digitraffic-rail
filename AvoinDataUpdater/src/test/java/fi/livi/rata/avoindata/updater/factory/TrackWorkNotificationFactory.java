package fi.livi.rata.avoindata.updater.factory;

import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.dao.train.TimeTableRowRepository;
import fi.livi.rata.avoindata.common.dao.train.TrainRepository;
import fi.livi.rata.avoindata.common.domain.common.StationEmbeddable;
import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class TrackWorkNotificationFactory {

    private static final Random random = new Random(System.nanoTime());

    @Autowired
    private TrackWorkNotificationRepository repository;

    @Transactional
    public List<TrackWorkNotification> createPersist(int versions) {
        List<TrackWorkNotification> twns = create(versions);
        repository.saveAll(twns);
        return twns;
    }

    @Transactional
    public List<TrackWorkNotification> create(int versions) {
        int id = random.nextInt(99999);
        return IntStream.rangeClosed(1, versions).mapToObj(v -> {
            TrackWorkNotification trackWorkNotification = new TrackWorkNotification(
                    new TrackWorkNotification.TrackWorkNotificationId(id, v),
                    TrackWorkNotificationState.DRAFT,
                    UUID.randomUUID().toString(),
                    ZonedDateTime.now().minusHours(random.nextInt(100)).withNano(0),
                    ZonedDateTime.now().withNano(0),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean(),
                    random.nextBoolean());
            return trackWorkNotification;
        }).collect(Collectors.toList());
    }

}
