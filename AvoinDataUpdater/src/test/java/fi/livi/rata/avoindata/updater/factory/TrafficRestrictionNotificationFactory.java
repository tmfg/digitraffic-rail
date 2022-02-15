package fi.livi.rata.avoindata.updater.factory;

import org.locationtech.jts.geom.Point;
import fi.livi.rata.avoindata.common.dao.trafficrestriction.TrafficRestrictionNotificationRepository;
import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotification;
import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionNotificationState;
import static fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.*;

import fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static fi.livi.rata.avoindata.common.domain.trafficrestriction.TrafficRestrictionType.FIREWORK_DANGER_ZONE;
import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN;
import static fi.livi.rata.avoindata.updater.CoordinateTestData.TAMPERE_COORDINATE_TM35FIN_DEVIATED;

@Component
public class TrafficRestrictionNotificationFactory extends RumaNotificationFactoryBase {

    private static final Random random = new Random(System.nanoTime());

    @Autowired
    private TrafficRestrictionNotificationRepository repository;

    @Transactional
    public List<TrafficRestrictionNotification> createPersist(int versions) {
        final List<TrafficRestrictionNotification> twns = create(versions);
        repository.saveAll(twns);
        return twns;
    }

    public List<TrafficRestrictionNotification> create(int versions) {
        final Point geometryMap = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN);
        final Point geometrySchema = geometryFactory.createPoint(TAMPERE_COORDINATE_TM35FIN_DEVIATED);
        geometryMap.setSRID(SpatialConstants.WGS84_SRID);
        geometrySchema.setSRID(SpatialConstants.WGS84_SRID);
        final List<TrafficRestrictionType> types = Arrays.asList(
                CLOSED_FROM_TRAFFIC,
                CLOSED_FROM_ELECTRIC_ROLLING_STOCK,
                TEMPORARY_SPEED_LIMIT,
                AXLE_WEIGHT_MAX,
                ATP_CONSTRUCTION_ZONE,
                SWITCH_LOCKED,
                FIREWORK_DANGER_ZONE);
        Collections.shuffle(types);
        final String id = UUID.randomUUID().toString();
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
                        ZonedDateTime.now().withNano(0),
                        random.nextBoolean() ? ZonedDateTime.now().withNano(0) : null,
                        random.nextBoolean() ? ZonedDateTime.now().withNano(0) : null,
                        geometryMap,
                        geometrySchema)
        ).collect(Collectors.toList());
    }

}
