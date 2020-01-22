package fi.livi.rata.avoindata.server.controller.api.ruma;

import fi.livi.rata.avoindata.common.domain.spatial.Geometry;
import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;

public class RumaLocationDto {

    public final  LocationType locationType;
    public final  String operatingPointId;
    public final String sectionBetweenOperatingPointsId;
    public final Geometry<?> locationMap;
    public final Geometry<?> locationSchema;

    public RumaLocationDto(
            final LocationType locationType,
            final String operatingPointId,
            final String sectionBetweenOperatingPointsId,
            final Geometry<?> locationMap,
            final Geometry<?> locationSchema)
    {
        this.locationType = locationType;
        this.operatingPointId = operatingPointId;
        this.sectionBetweenOperatingPointsId = sectionBetweenOperatingPointsId;
        this.locationMap = locationMap;
        this.locationSchema = locationSchema;
    }
}
