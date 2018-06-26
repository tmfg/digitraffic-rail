package fi.livi.rata.avoindata.server.factory;

import com.google.common.collect.Lists;
import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.composition.*;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class CompositionFactory {
    @Autowired
    private CompositionRepository compositionRepository;

    public Composition create() {
        Operator operator = new Operator();
        operator.operatorUICCode = 1;
        operator.operatorShortCode = "vr";
        Composition composition = new Composition(operator, 51L, LocalDate.now(), 1L, 1L, 1L);

        CompositionTimeTableRow beginTimeTableRow = createCompositionTimeTableRow(composition, LocalDateTime.now(), "HKI", 1);
        CompositionTimeTableRow endTimeTableRow = createCompositionTimeTableRow(composition, LocalDateTime.now(), "TPE", 2);

        JourneySection journeySection = new JourneySection(beginTimeTableRow, endTimeTableRow, composition, 200, 1000);

        Locomotive locomotive = new Locomotive();
        locomotive.location = 1;
        locomotive.powerTypeAbbreviation = "D";
        locomotive.powerType = "Diesel";
        locomotive.locomotiveType = "Dm12";
        locomotive.journeysection = journeySection;
        journeySection.locomotives.add(locomotive);

        composition.journeySections.add(journeySection);

        compositionRepository.persist(Lists.newArrayList(composition));

        return composition;
    }

    private CompositionTimeTableRow createCompositionTimeTableRow(Composition composition, LocalDateTime scheduledTime, String stationShortCode, int stationUICCode) {
        JourneyCompositionRow journeyCompositionRow = new JourneyCompositionRow(scheduledTime, stationShortCode, stationUICCode, "fi", TimeTableRow.TimeTableRowType.DEPARTURE);
        return new CompositionTimeTableRow(journeyCompositionRow, composition);
    }
}
