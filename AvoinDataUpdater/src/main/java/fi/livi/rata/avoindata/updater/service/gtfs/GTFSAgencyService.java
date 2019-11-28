package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Agency;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class GTFSAgencyService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    public Map<String, String> urls = new HashMap<>();

    @PostConstruct
    private void setup() {
        urls.put("vr", "https://vr.fi");
    }

    public List<Agency> createAgencies(Map<Long, Map<List<LocalDate>, Schedule>> scheduleIntervalsByTrain) {
        List<Agency> agencies = new ArrayList<>();

        Map<Integer, Operator> operators = new HashMap<>();
        for (final Map<List<LocalDate>, Schedule> trainsSchedules : scheduleIntervalsByTrain.values()) {
            for (final Schedule schedule : trainsSchedules.values()) {
                operators.putIfAbsent(schedule.operator.operatorUICCode, schedule.operator);
            }
        }


        for (final Operator operator : operators.values()) {
            final Agency agency = new Agency();
            agency.name = operator.operatorShortCode;
            agency.id = operator.operatorUICCode;
            String url = getUrl(operator, agency);
            agency.url = url;
            agency.timezone = "Europe/Helsinki";
            agencies.add(agency);
        }

        return agencies;
    }

    private String getUrl(Operator operator, Agency agency) {
        String url = urls.get(operator.operatorShortCode);
        if (url == null) {
            agency.url = "https://google.com";
            log.warn("Url not found for operator {}", operator.operatorShortCode);
        }
        return url;
    }
}
