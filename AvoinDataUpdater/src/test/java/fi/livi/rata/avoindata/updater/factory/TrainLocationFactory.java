package fi.livi.rata.avoindata.updater.factory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import org.osgeo.proj4j.ProjCoordinate;
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
