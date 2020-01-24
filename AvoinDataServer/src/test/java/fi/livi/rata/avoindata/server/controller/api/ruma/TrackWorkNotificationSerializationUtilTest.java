package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.server.BaseTest;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;
import fi.livi.rata.avoindata.server.factory.TrackWorkNotificationFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static fi.livi.rata.avoindata.server.controller.api.ruma.TrackWorkNotificationSerializationUtil.*;
import static org.junit.Assert.assertEquals;

public class TrackWorkNotificationSerializationUtilTest extends BaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    private static final Random random = new Random(System.nanoTime());
    private final GeometryFactory geometryFactory = new GeometryFactory();

    private final double ALLOWED_DELTA = 0.0;

    @Test
    public void dto_twn_map() {
        TrackWorkNotification twn = factory.create(1).get(0);

        SpatialTrackWorkNotificationDto twnDto = toTwnDto(twn, false);
        Point twnLocation = (Point) twn.locationMap;
        fi.livi.rata.avoindata.common.domain.spatial.Point twnDtoLocation = (fi.livi.rata.avoindata.common.domain.spatial.Point) twnDto.location;

        assertEquals(twnLocation.getX(), twnDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(twnLocation.getY(), twnDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void dto_twn_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);

        SpatialTrackWorkNotificationDto twnDto = toTwnDto(twn, true);
        Point twnLocation = (Point) twn.locationSchema;
        fi.livi.rata.avoindata.common.domain.spatial.Point twnDtoLocation = (fi.livi.rata.avoindata.common.domain.spatial.Point) twnDto.location;

        assertEquals(twnLocation.getX(), twnDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(twnLocation.getY(), twnDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void dto_rumaLocation_map() {
        TrackWorkNotification twn = factory.create(1).get(0);
        RumaLocation rl = createRumaLocation();

        SpatialRumaLocationDto rlDto = toRumaLocationDto(twn, rl, false);
        fi.livi.rata.avoindata.common.domain.spatial.LineString rlDtoLocation = (fi.livi.rata.avoindata.common.domain.spatial.LineString) rlDto.location;

        assertEquals(rl.locationMap.getCoordinates()[0].x, rlDtoLocation.getCoordinates().get(0).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationMap.getCoordinates()[0].y, rlDtoLocation.getCoordinates().get(0).get(1), ALLOWED_DELTA);
        assertEquals(rl.locationMap.getCoordinates()[1].x, rlDtoLocation.getCoordinates().get(1).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationMap.getCoordinates()[1].y, rlDtoLocation.getCoordinates().get(1).get(1), ALLOWED_DELTA);
    }

    @Test
    public void dto_rumaLocation_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);
        RumaLocation rl = createRumaLocation();

        SpatialRumaLocationDto rlDto = toRumaLocationDto(twn, rl, true);
        fi.livi.rata.avoindata.common.domain.spatial.LineString rlDtoLocation = (fi.livi.rata.avoindata.common.domain.spatial.LineString) rlDto.location;

        assertEquals(rl.locationSchema.getCoordinates()[0].x, rlDtoLocation.getCoordinates().get(0).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationSchema.getCoordinates()[0].y, rlDtoLocation.getCoordinates().get(0).get(1), ALLOWED_DELTA);
        assertEquals(rl.locationSchema.getCoordinates()[1].x, rlDtoLocation.getCoordinates().get(1).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationSchema.getCoordinates()[1].y, rlDtoLocation.getCoordinates().get(1).get(1), ALLOWED_DELTA);
    }

    @Test
    public void dto_identifierRange_map() {
        TrackWorkNotification twn = factory.create(1).get(0);
        IdentifierRange ir = createIdentifierRange();

        SpatialIdentifierRangeDto irDto = toIdentifierRangeDto(twn, ir, false);
        Point irLocation = (Point) ir.locationMap;
        fi.livi.rata.avoindata.common.domain.spatial.Point irDtoLocation = (fi.livi.rata.avoindata.common.domain.spatial.Point) irDto.location;

        assertEquals(irLocation.getX(), irDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(irLocation.getY(), irDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void dto_identifierRange_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);
        IdentifierRange ir = createIdentifierRange();

        SpatialIdentifierRangeDto irDto = toIdentifierRangeDto(twn, ir, true);
        Point irLocation = (Point) ir.locationSchema;
        fi.livi.rata.avoindata.common.domain.spatial.Point irDtoLocation = (fi.livi.rata.avoindata.common.domain.spatial.Point) irDto.location;

        assertEquals(irLocation.getX(), irDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(irLocation.getY(), irDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void geoJson_featureAmount_twn() {
        TrackWorkNotification twn = factory.create(1).get(0);

        assertEquals(1, toFeatures(twn, false).count());
    }

    @Test
    public void geoJson_featureAmount_rumaLocation() {
        TrackWorkNotification twn = factory.create(1).get(0);
        TrackWorkPart twp = new TrackWorkPart();
        twp.locations = Set.of(createRumaLocation());
        twn.trackWorkParts = Set.of(twp);

        assertEquals(2, toFeatures(twn, false).count());
    }

    @Test
    public void geoJson_featureAmount_identifierRange() {
        TrackWorkNotification twn = factory.create(1).get(0);
        TrackWorkPart twp = new TrackWorkPart();
        RumaLocation loc = createRumaLocation();
        IdentifierRange ir = createIdentifierRange();
        loc.identifierRanges = Set.of(ir);
        twp.locations = Set.of(loc);
        twn.trackWorkParts = Set.of(twp);

        assertEquals(2, toFeatures(twn, false).count());
    }

    @Test
    public void geoJson_twn_map() {
        TrackWorkNotification twn = factory.create(1).get(0);

        Feature f = toFeatures(twn, false).collect(Collectors.toList()).get(0);

        assertEquals(twn.locationMap.getCoordinate(), f.geometry.getCoordinate());
    }

    @Test
    public void geoJson_twn_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);

        Feature f = toFeatures(twn, true).collect(Collectors.toList()).get(0);

        assertEquals(twn.locationSchema.getCoordinate(), f.geometry.getCoordinate());
    }

    private RumaLocation createRumaLocation() {
        RumaLocation loc = new RumaLocation();
        loc.locationType = LocationType.WORK;
        loc.operatingPointId = UUID.randomUUID().toString();
        loc.identifierRanges = Collections.emptySet();
        loc.locationMap = geometryFactory.createLineString(new Coordinate[]{new Coordinate(random.nextLong(), random.nextLong()), new Coordinate(random.nextLong(), random.nextLong())});
        loc.locationSchema = geometryFactory.createLineString(new Coordinate[]{new Coordinate(random.nextLong(), random.nextLong()), new Coordinate(random.nextLong(), random.nextLong())});
        return loc;
    }

    private IdentifierRange createIdentifierRange() {
        IdentifierRange ir = new IdentifierRange();
        ir.elementRanges = Collections.emptySet();
        ir.speedLimit = null;
        ir.elementId = UUID.randomUUID().toString();
        ir.locationMap = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        ir.locationSchema = geometryFactory.createPoint(new Coordinate(random.nextLong(), random.nextLong()));
        return ir;
    }
}
