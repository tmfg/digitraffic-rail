package fi.livi.rata.avoindata.server.factory;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.trackwork.TrackWorkNotificationRepository;
import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;
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
        Point geometryMap = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        Point geometrySchema = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
        final String id = UUID.randomUUID().toString();
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
                        geometryMap,
                        geometrySchema)
        ).collect(Collectors.toList());
    }

    public TrackWorkPart createWorkPart() {
        final TrackWorkPart twp = new TrackWorkPart();
        twp.locations = Collections.emptySet();
        twp.containsFireWork = random.nextBoolean();
        twp.partIndex = (long) (1 + random.nextInt(49));
        twp.permissionMinimumDuration = Duration.ofMinutes(random.nextInt(600));
        twp.plannedWorkingGap = LocalTime.now();
        twp.startDay = LocalDate.now();
        return twp;
    }

    public RumaLocation createRumaLocation() {
        LineString geometryMap = geometryFactory.createLineString(new Coordinate[]{new Coordinate(random.nextLong(), random.nextLong()), new Coordinate(random.nextLong(), random.nextLong())});
        LineString geometrySchema = geometryFactory.createLineString(new Coordinate[]{new Coordinate(random.nextLong(), random.nextLong()), new Coordinate(random.nextLong(), random.nextLong())});
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
        final RumaLocation loc = new RumaLocation();
        loc.locationType = LocationType.WORK;
        loc.operatingPointId = UUID.randomUUID().toString();
        loc.identifierRanges = Collections.emptySet();
        loc.locationMap = geometryMap;
        loc.locationSchema = geometrySchema;
        return loc;
    }

    public IdentifierRange createIdentifierRange() {
        Point geometryMap = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        Point geometrySchema = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
        final IdentifierRange ir = new IdentifierRange();
        ir.elementRanges = Collections.emptySet();
        ir.speedLimit = null;
        ir.elementId = UUID.randomUUID().toString();
        ir.locationMap = geometryMap;
        ir.locationSchema = geometrySchema;
        return ir;
    }

}
