package fi.livi.rata.avoindata.server.factory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.trackwork.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Collections;
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
        final List<TrackWorkNotification> twns = create(versions);
        repository.saveAll(twns);
        return twns;
    }

    public List<TrackWorkNotification> create(int versions) {
        final long id = random.nextInt(99999);
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
                        geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong())),
                        geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong())))
        ).collect(Collectors.toList());
    }

    public TrackWorkPart createTrackWorkPart() {
        final TrackWorkPart twp = new TrackWorkPart();
        twp.startDay = LocalDate.now();
        twp.partIndex = (long) random.nextInt(50);
        twp.permissionMinimumDuration = Duration.ofMinutes(random.nextLong());
        twp.plannedWorkingGap = LocalTime.now();
        twp.containsFireWork = false;
        return twp;
    }

    public RumaLocation createRumaLocation() {
        final RumaLocation loc = new RumaLocation();
        loc.locationType = LocationType.WORK;
        loc.operatingPointId = UUID.randomUUID().toString();
        loc.identifierRanges = Collections.emptySet();
        loc.locationMap = geometryFactory.createLineString(new Coordinate[]{new Coordinate(random.nextLong(), random.nextLong()), new Coordinate(random.nextLong(), random.nextLong())});
        loc.locationSchema = geometryFactory.createLineString(new Coordinate[]{new Coordinate(random.nextLong(), random.nextLong()), new Coordinate(random.nextLong(), random.nextLong())});
        return loc;
    }

    public IdentifierRange createIdentifierRange() {
        final IdentifierRange ir = new IdentifierRange();
        ir.elementRanges = Collections.emptySet();
        ir.speedLimit = null;
        ir.elementId = UUID.randomUUID().toString();
        ir.locationMap = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        ir.locationSchema = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        return ir;
    }

}
