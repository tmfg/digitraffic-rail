package fi.livi.rata.avoindata.updater.factory;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TrainLocationFactory {
    private GeometryFactory geometryFactory = new GeometryFactory();

  @Autowired
  private Wgs84ConversionService wgs84ConversionService;

    @Transactional
    public TrainLocation create(double x, double y) {
        final TrainLocation trainLocation = new TrainLocation();

        final double iCoordinate = x;
        final double pCoordinate = y;
        final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(iCoordinate, pCoordinate);
        trainLocation.location = geometryFactory.createPoint(new Coordinate(projCoordinate.x, projCoordinate.y));
        trainLocation.liikeLocation = geometryFactory.createPoint(new Coordinate(iCoordinate, pCoordinate));

        return trainLocation;
    }
}
