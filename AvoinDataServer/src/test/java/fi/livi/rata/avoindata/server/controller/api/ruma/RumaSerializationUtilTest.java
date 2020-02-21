package fi.livi.rata.avoindata.server.controller.api.ruma;

import com.vividsolutions.jts.geom.Point;
import fi.livi.rata.avoindata.common.domain.spatial.LineStringDto;
import fi.livi.rata.avoindata.common.domain.spatial.PointDto;
import fi.livi.rata.avoindata.common.domain.trackwork.*;
import fi.livi.rata.avoindata.server.BaseTest;
import fi.livi.rata.avoindata.server.controller.api.geojson.Feature;
import fi.livi.rata.avoindata.server.factory.TrackWorkNotificationFactory;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

import static fi.livi.rata.avoindata.server.controller.api.ruma.RumaSerializationUtil.*;
import static org.junit.Assert.assertEquals;

public class RumaSerializationUtilTest extends BaseTest {

    @Autowired
    private TrackWorkNotificationFactory factory;

    private final double ALLOWED_DELTA = 0.0;

    @Test
    public void dto_twn_map() {
        TrackWorkNotification twn = factory.create(1).get(0);

        SpatialTrackWorkNotificationDto twnDto = toTwnDto(twn, false);
        Point twnLocation = (Point) twn.locationMap;
        PointDto twnDtoLocation = (PointDto) twnDto.location;

        assertEquals(twnLocation.getX(), twnDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(twnLocation.getY(), twnDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void dto_twn_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);

        SpatialTrackWorkNotificationDto twnDto = toTwnDto(twn, true);
        Point twnLocation = (Point) twn.locationSchema;
        PointDto twnDtoLocation = (PointDto) twnDto.location;

        assertEquals(twnLocation.getX(), twnDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(twnLocation.getY(), twnDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void dto_rumaLocation_map() {
        TrackWorkNotification twn = factory.create(1).get(0);
        RumaLocation rl = factory.createRumaLocation();

        SpatialRumaLocationDto rlDto = toRumaLocationDto(twn.id.id, rl, false);
        LineStringDto rlDtoLocation = (LineStringDto) rlDto.location;

        assertEquals(rl.locationMap.getCoordinates()[0].x, rlDtoLocation.getCoordinates().get(0).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationMap.getCoordinates()[0].y, rlDtoLocation.getCoordinates().get(0).get(1), ALLOWED_DELTA);
        assertEquals(rl.locationMap.getCoordinates()[1].x, rlDtoLocation.getCoordinates().get(1).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationMap.getCoordinates()[1].y, rlDtoLocation.getCoordinates().get(1).get(1), ALLOWED_DELTA);
    }

    @Test
    public void dto_rumaLocation_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);
        RumaLocation rl = factory.createRumaLocation();

        SpatialRumaLocationDto rlDto = toRumaLocationDto(twn.id.id, rl, true);
        LineStringDto rlDtoLocation = (LineStringDto) rlDto.location;

        assertEquals(rl.locationSchema.getCoordinates()[0].x, rlDtoLocation.getCoordinates().get(0).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationSchema.getCoordinates()[0].y, rlDtoLocation.getCoordinates().get(0).get(1), ALLOWED_DELTA);
        assertEquals(rl.locationSchema.getCoordinates()[1].x, rlDtoLocation.getCoordinates().get(1).get(0), ALLOWED_DELTA);
        assertEquals(rl.locationSchema.getCoordinates()[1].y, rlDtoLocation.getCoordinates().get(1).get(1), ALLOWED_DELTA);
    }

    @Test
    public void dto_identifierRange_map() {
        TrackWorkNotification twn = factory.create(1).get(0);
        IdentifierRange ir = factory.createIdentifierRange();

        SpatialIdentifierRangeDto irDto = toIdentifierRangeDto(twn.id.id, ir, false);
        Point irLocation = (Point) ir.locationMap;
        PointDto irDtoLocation = (PointDto) irDto.location;

        assertEquals(irLocation.getX(), irDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(irLocation.getY(), irDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void dto_identifierRange_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);
        IdentifierRange ir = factory.createIdentifierRange();

        SpatialIdentifierRangeDto irDto = toIdentifierRangeDto(twn.id.id, ir, true);
        Point irLocation = (Point) ir.locationSchema;
        PointDto irDtoLocation = (PointDto) irDto.location;

        assertEquals(irLocation.getX(), irDtoLocation.longitude, ALLOWED_DELTA);
        assertEquals(irLocation.getY(), irDtoLocation.latitude, ALLOWED_DELTA);
    }

    @Test
    public void geoJson_featureAmount_twn() {
        TrackWorkNotification twn = factory.create(1).get(0);

        assertEquals(1, toTwnFeatures(twn, false).count());
    }

    @Test
    public void geoJson_featureAmount_rumaLocation() {
        TrackWorkNotification twn = factory.create(1).get(0);
        TrackWorkPart twp = new TrackWorkPart();
        twp.locations = Set.of(factory.createRumaLocation());
        twn.trackWorkParts = Set.of(twp);

        assertEquals(2, toTwnFeatures(twn, false).count());
    }

    @Test
    public void geoJson_featureAmount_identifierRange() {
        TrackWorkNotification twn = factory.create(1).get(0);
        TrackWorkPart twp = new TrackWorkPart();
        RumaLocation loc = factory.createRumaLocation();
        IdentifierRange ir = factory.createIdentifierRange();
        loc.identifierRanges = Set.of(ir);
        twp.locations = Set.of(loc);
        twn.trackWorkParts = Set.of(twp);

        assertEquals(2, toTwnFeatures(twn, false).count());
    }

    @Test
    public void geoJson_twn_map() {
        TrackWorkNotification twn = factory.create(1).get(0);

        Feature f = toTwnFeatures(twn, false).collect(Collectors.toList()).get(0);

        assertEquals(twn.locationMap.getCoordinate(), f.geometry.getCoordinate());
    }

    @Test
    public void geoJson_twn_schema() {
        TrackWorkNotification twn = factory.create(1).get(0);

        Feature f = toTwnFeatures(twn, true).collect(Collectors.toList()).get(0);

        assertEquals(twn.locationSchema.getCoordinate(), f.geometry.getCoordinate());
    }

}
