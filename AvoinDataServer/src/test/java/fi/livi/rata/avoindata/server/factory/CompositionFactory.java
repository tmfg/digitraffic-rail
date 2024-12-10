package fi.livi.rata.avoindata.server.factory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.domain.common.Operator;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.CompositionTimeTableRow;
import fi.livi.rata.avoindata.common.domain.composition.JourneyCompositionRow;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.common.utils.DateProvider;

@Component
public class CompositionFactory {
    @Autowired
    private CompositionRepository compositionRepository;

    public Composition create(final long version, final Instant messageDateTime) {
        final Operator operator = new Operator();
        operator.operatorUICCode = 1;
        operator.operatorShortCode = "vr";
        final Composition composition = new Composition(operator, 51L, LocalDate.now(), 1L, 1L, version, messageDateTime);

        final CompositionTimeTableRow beginTimeTableRow = createCompositionTimeTableRow(DateProvider.nowInHelsinki(), "HKI", 1);
        final CompositionTimeTableRow endTimeTableRow = createCompositionTimeTableRow(DateProvider.nowInHelsinki(), "TPE", 2);

        final JourneySection journeySection = new JourneySection(beginTimeTableRow, endTimeTableRow, composition, 200, 1000, 0L, 0L);

        final Locomotive locomotive = new Locomotive();
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

    private CompositionTimeTableRow createCompositionTimeTableRow(final ZonedDateTime scheduledTime, final String stationShortCode, final int stationUICCode) {
        final ZonedDateTime scheduledTimeUtc = scheduledTime.withZoneSameInstant(ZoneId.of("UTC"));
        final JourneyCompositionRow journeyCompositionRow = new JourneyCompositionRow(scheduledTimeUtc, stationShortCode, stationUICCode, "fi", TimeTableRow.TimeTableRowType.DEPARTURE);
        return new CompositionTimeTableRow(journeyCompositionRow);
    }
}
