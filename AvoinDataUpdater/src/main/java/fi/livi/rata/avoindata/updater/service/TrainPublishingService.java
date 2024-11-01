package fi.livi.rata.avoindata.updater.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fi.livi.rata.avoindata.common.domain.jsonview.TrainJsonView;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.domain.train.Train;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TrainPublishingService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MQTTPublishService mqttPublishService;

    @Autowired
    private ObjectMapper objectMapper;

    public void publish(final List<Train> updatedTrains) {
        final StopWatch sw = StopWatch.createStarted();

        try {
            if (updatedTrains != null) {
                for (final Train train : updatedTrains) {
                    final String trainAsString = objectMapper.writerWithView(TrainJsonView.LiveTrains.class).writeValueAsString(train);

                    mqttPublishService.publishString(
                            String.format("trains/%s/%s/%s/%s/%s/%s/%s/%s", train.id.departureDate, train.id.trainNumber,
                                    train.trainCategory, train.trainType, train.operator.operatorShortCode, train.commuterLineID,
                                    train.runningCurrently, train.timetableType), trainAsString);

                    final Set<String> announcedStations = new HashSet<>();
                    for (final TimeTableRow timeTableRow : train.timeTableRows) {
                        final String stationShortCode = timeTableRow.station.stationShortCode;
                        if (!announcedStations.contains(stationShortCode)) {
                            announcedStations.add(stationShortCode);
                            mqttPublishService.publishString(String.format("trains-by-station/%s", stationShortCode), trainAsString);
                        }
                    }
                }
            }
        } catch (final Exception e) {
            log.error("Error publishing trains to MQTT", e);
        } finally {
            log.info("Publishing {} trains took {} ms", updatedTrains.size(), sw.getTime());

        }
    }
}
