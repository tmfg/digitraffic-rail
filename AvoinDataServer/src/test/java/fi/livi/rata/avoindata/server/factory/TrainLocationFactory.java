package fi.livi.rata.avoindata.server.factory;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import fi.livi.rata.avoindata.common.dao.trainlocation.TrainLocationRepository;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocation;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationConnectionQuality;
import fi.livi.rata.avoindata.common.domain.trainlocation.TrainLocationId;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TrainLocationFactory {
    @Autowired
    private DateProvider dp;

    @Autowired
    private TrainLocationRepository trainLocationRepository;


    private GeometryFactory geometryFactory = new GeometryFactory();

    public TrainLocation createTrainLocation() {
        return this.createTrainLocation(new TrainLocationId(1L, dp.dateInHelsinki(), dp.nowInHelsinki()));
    }

    public TrainLocation createTrainLocation(TrainLocationId trainLocationId) {
        TrainLocation trainLocation = new TrainLocation();
        trainLocation.location = geometryFactory.createPoint(new Coordinate(20.3, 10.1));
        trainLocation.trainLocationId = trainLocationId;
        trainLocation.connectionQuality = TrainLocationConnectionQuality.BREAKING;
        trainLocation.speed = 100;

        return trainLocationRepository.save(trainLocation);
    }
}
