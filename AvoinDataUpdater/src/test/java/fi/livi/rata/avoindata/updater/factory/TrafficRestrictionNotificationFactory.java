package fi.livi.rata.avoindata.updater.factory;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN;
import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN_DEVIATED;

@Component
public class TrafficRestrictionNotificationFactory {

    private static final Random random = new Random(System.nanoTime());

    @Autowired
    private TrafficRestrictionNotificationRepository repository;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Transactional
    public List<TrafficRestrictionNotification> createPersist(int versions) {
        final List<TrafficRestrictionNotification> twns = create(versions);
        repository.saveAll(twns);
        return twns;
    }

    public List<TrafficRestrictionNotification> create(int versions) {
        Point geometryMap = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN);
        Point geometrySchema = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN_DEVIATED);
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
        List<TrafficRestrictionType> types = Arrays.asList(TrafficRestrictionType.values());
        Collections.shuffle(types);
        final long id = random.nextInt(99999);
        return LongStream.rangeClosed(1, versions).mapToObj(v ->
                new TrafficRestrictionNotification(
                        new TrafficRestrictionNotification.TrafficRestrictionNotificationId(id, v),
                        TrafficRestrictionNotificationState.SENT,
                        UUID.randomUUID().toString(),
                        ZonedDateTime.now().minusHours(random.nextInt(100)).withNano(0),
                        ZonedDateTime.now().withNano(0),
                        types.get(0),
                        random.nextBoolean() ? UUID.randomUUID().toString() : null,
                        random.nextBoolean() ? random.nextDouble() : null,
                        random.nextBoolean() ? ZonedDateTime.now().withNano(0) : null,
                        random.nextBoolean() ? ZonedDateTime.now().withNano(0) : null,
                        random.nextBoolean() ? ZonedDateTime.now().withNano(0) : null,
                        geometryMap,
                        geometrySchema)
        ).collect(Collectors.toList());
    }

}
