package fi.livi.rata.avoindata.server.factory;

import fi.livi.rata.avoindata.common.dao.trainrunningmessage.TrainRunningMessageRepository;
import fi.livi.rata.avoindata.common.domain.common.StringTrainId;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessage;
import fi.livi.rata.avoindata.common.domain.trainreadymessage.TrainRunningMessageTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class TrainRunningMessageFactory {
    @Autowired
    private TrainRunningMessageRepository trainRunningMessageRepository;

    public TrainRunningMessage create() {
        LocalDate departureDate = LocalDate.of(2018, 1, 1);

        TrainRunningMessage trainRunningMessage = new TrainRunningMessage();
        trainRunningMessage.virtualDepartureDate = LocalDate.of(2018, 1, 1);
        trainRunningMessage.version = 1L;
        trainRunningMessage.id = 1L;
        trainRunningMessage.trainId = new StringTrainId("1", departureDate);
        trainRunningMessage.timestamp = ZonedDateTime.of(departureDate, LocalTime.of(8, 0), ZoneId.of("Europe/Helsinki"));
        trainRunningMessage.type = TrainRunningMessageTypeEnum.OCCUPY;
        trainRunningMessage.station = "PSL";
        trainRunningMessage.trackSection = "RAIDE_1";

        return trainRunningMessageRepository.save(trainRunningMessage);
    }
}
