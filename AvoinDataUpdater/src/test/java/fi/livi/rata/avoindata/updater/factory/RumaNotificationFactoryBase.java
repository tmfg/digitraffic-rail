package fi.livi.rata.avoindata.updater.factory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.domain.spatial.SpatialConstants;
import fi.livi.rata.avoindata.common.domain.trackwork.IdentifierRange;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import fi.livi.rata.avoindata.common.domain.trackwork.RumaLocation;

import java.util.Collections;
import java.util.UUID;

import static fi.livi.rata.avoindata.updater.CoordinateTestData.*;

public abstract class RumaNotificationFactoryBase {

    protected final GeometryFactory geometryFactory = new GeometryFactory();

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
