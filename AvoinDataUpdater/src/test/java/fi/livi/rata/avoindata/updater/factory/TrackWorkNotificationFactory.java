package fi.livi.rata.avoindata.updater.factory;

import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotification;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkNotificationState;
import fi.livi.rata.avoindata.common.domain.trackwork.TrackWorkPart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN;
import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN_DEVIATED;

@Component
public class TrackWorkNotificationFactory extends RumaNotificationFactoryBase {

    private static final Random random = new Random(System.nanoTime());

    @Autowired
    private TrackWorkNotificationRepository repository;

    @Transactional
    public List<TrackWorkNotification> createPersist(int versions) {
        final List<TrackWorkNotification> twns = create(versions);
        repository.saveAll(twns);
        return twns;
    }

    public List<TrackWorkNotification> create(int versions) {
        final Point geometryMap = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN);
        final Point geometrySchema = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN_DEVIATED);
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
        final String id = UUID.randomUUID().toString();
        return LongStream.rangeClosed(1, versions).mapToObj(v ->
                new TrackWorkNotification(
                        new TrackWorkNotification.TrackWorkNotificationId(id, v),
                        TrackWorkNotificationState.SENT,
                        UUID.randomUUID().toString(),
                        ZonedDateTime.now().minusHours(random.nextInt(100)).withNano(0),
                        ZonedDateTime.now().withNano(0),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        random.nextBoolean(),
                        geometryMap,
                        geometrySchema)
        ).collect(Collectors.toList());
    }

    public TrackWorkPart createTrackWorkPart() {
        final TrackWorkPart twp = new TrackWorkPart();
        twp.startDay = LocalDate.now();
        twp.partIndex = (long) random.nextInt(50);
        twp.permissionMinimumDuration = Duration.ofMinutes(random.nextInt(60));
        twp.plannedWorkingGap = LocalTime.now();
        twp.containsFireWork = false;
        return twp;
    }

}
