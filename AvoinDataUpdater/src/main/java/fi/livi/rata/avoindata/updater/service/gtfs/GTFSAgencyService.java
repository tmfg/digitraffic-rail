package fi.livi.rata.avoindata.updater.service.gtfs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import fi.livi.rata.avoindata.common.dao.metadata.OperatorRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Agency;
import fi.livi.rata.avoindata.updater.service.timetable.entities.Schedule;

@Service
public class GTFSAgencyService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private OperatorRepository operatorRepository;

    public Map<String, String> urls = new HashMap<>();
    public Map<String, String> names = new HashMap<>();

    @PostConstruct
    private void setup() {
        names.put("vr", "VR");
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
            agency.name = getName(operator.operatorShortCode);
            agency.id = operator.operatorUICCode;
            agency.url = getUrl(operator, agency);
            agency.timezone = "Europe/Helsinki";
            agencies.add(agency);
        }

        return agencies;
    }

    private String getName(String operatorShortCode) {
        String name = names.get(operatorShortCode);
        if (name != null) {
            return name;
        }

        fi.livi.rata.avoindata.common.domain.metadata.Operator operator = operatorRepository.findByOperatorShortCode(operatorShortCode);
        if (operator != null) {
            return operator.operatorName;
        } else {
            return operatorShortCode;
        }
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
