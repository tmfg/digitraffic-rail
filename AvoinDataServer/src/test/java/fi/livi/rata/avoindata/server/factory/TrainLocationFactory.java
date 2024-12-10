package fi.livi.rata.avoindata.server.factory;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class TrainLocationFactory {

    @Autowired
    private TrainLocationRepository trainLocationRepository;


    private final GeometryFactory geometryFactory = new GeometryFactory();

    public TrainLocation createTrainLocation() {
        return this.createTrainLocation(new TrainLocationId(1L, DateProvider.dateInHelsinki(), DateProvider.nowInHelsinki()));
    }

    public TrainLocation createTrainLocation(final TrainLocationId trainLocationId) {
        final TrainLocation trainLocation = new TrainLocation();
        trainLocation.location = geometryFactory.createPoint(new Coordinate(20.3, 10.1));
        trainLocation.trainLocationId = trainLocationId;
        trainLocation.speed = 100;

        return trainLocationRepository.save(trainLocation);
    }
}
