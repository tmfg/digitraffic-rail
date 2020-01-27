package fi.livi.rata.avoindata.updater.factory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
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
import static fi.livi.rata.avoindata.updater.CoordinateTestData.*;

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
        Point geometryMap = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN);
        Point geometrySchema = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN_DEVIATED);
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
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

    public RumaLocation createRumaLocation() {
        final RumaLocation loc = new RumaLocation();
        loc.locationType = LocationType.WORK;
        loc.operatingPointId = UUID.randomUUID().toString();
        loc.identifierRanges = Collections.emptySet();
        loc.locationMap = geometryFactory.createLineString(new Coordinate[]{TAMPERE_COORDINATE_TM35FIN, VUOSAARI_COORDINATE_TM35FIN});
        loc.locationSchema = geometryFactory.createLineString(new Coordinate[]{TAMPERE_COORDINATE_TM35FIN_DEVIATED, VUOSAARI_COORDINATE_TM35FIN_DEVIATED});
        loc.locationMap.setSRID(SpatialConstants.WGS84_SRID);
        loc.locationSchema.setSRID(SpatialConstants.WGS84_SRID);
        return loc;
    }

    public IdentifierRange createIdentifierRange() {
        final IdentifierRange ir = new IdentifierRange();
        ir.elementRanges = Collections.emptySet();
        ir.speedLimit = null;
        ir.elementId = UUID.randomUUID().toString();
        ir.locationMap = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN);
        ir.locationSchema = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN_DEVIATED);
        ir.locationMap.setSRID(SpatialConstants.WGS84_SRID);
        ir.locationSchema.setSRID(SpatialConstants.WGS84_SRID);
        return ir;
    }

}
