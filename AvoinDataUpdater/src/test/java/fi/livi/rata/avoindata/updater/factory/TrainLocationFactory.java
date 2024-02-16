package fi.livi.rata.avoindata.updater.factory;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.train.Train;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.updater.service.Wgs84ConversionService;
import org.locationtech.proj4j.ProjCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Component
public class TrainLocationFactory {
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Autowired
    private Wgs84ConversionService wgs84ConversionService;

    @Autowired
    private TrainLocationRepository trainLocationRepository;

    @Transactional
    public TrainLocation create(final double x, final double y) {
        final TrainLocation trainLocation = new TrainLocation();

        final double iCoordinate = x;
        final double pCoordinate = y;
        final ProjCoordinate projCoordinate = wgs84ConversionService.liviToWgs84(iCoordinate, pCoordinate);
        trainLocation.location = geometryFactory.createPoint(new Coordinate(projCoordinate.x, projCoordinate.y));
        trainLocation.liikeLocation = geometryFactory.createPoint(new Coordinate(iCoordinate, pCoordinate));

        return trainLocation;
    }

    @Transactional
    public TrainLocation create(final Train train) {
        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.location = geometryFactory.createPoint(new Coordinate(20.3, 10.1));
        trainLocation.trainLocationId = new TrainLocationId(train.id.trainNumber, train.id.departureDate, ZonedDateTime.now());
        trainLocation.speed = 100;

        return trainLocationRepository.save(trainLocation);
    }
}
