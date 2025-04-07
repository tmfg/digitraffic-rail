package fi.livi.rata.avoindata.server.factory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import org.apache.commons.lang3.tuple.Pair;
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


    public void createVersions(final Pair<Long, Integer>...versionCountPairs) {
        final List<Composition> compositions = new ArrayList<>();
        final AtomicReference<Instant> messageDateTime = new AtomicReference<>(Instant.now().minus(30, ChronoUnit.DAYS));
        for (final Pair<Long, Integer> versionCountPair : versionCountPairs) {
            final long version = versionCountPair.getLeft();
            final int count = versionCountPair.getRight();
            final List<Composition> newCompositions = IntStream.range(0, count).boxed()
                    .map(i -> createInternal(version, messageDateTime.get(), i))
                    .toList();
            messageDateTime.set(messageDateTime.get().plus(1, ChronoUnit.DAYS));
            compositions.addAll(newCompositions);
        }
        compositionRepository.persist(compositions);
    }

    public Composition create(final long version, final Instant messageDateTime) {
        final Composition composition = createInternal(version, messageDateTime, 51L);
        compositionRepository.persist(Lists.newArrayList(composition));
        return composition;
    }

    private Composition create(final long version, final Instant messageDateTime, final long trainNumber) {
        final Composition composition = createInternal(version, messageDateTime, trainNumber);
        compositionRepository.persist(Lists.newArrayList(composition));
        return composition;
    }

    private Composition createInternal(final long version, final Instant messageDateTime, final long trainNumber) {
        final Operator operator = new Operator();
        operator.operatorUICCode = 1;
        operator.operatorShortCode = "vr";
        final Composition composition = new Composition(operator, trainNumber, messageDateTime.atZone(ZoneOffset.UTC).toLocalDate(), 1L, 1L, version, messageDateTime);

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



        return composition;
    }

    private CompositionTimeTableRow createCompositionTimeTableRow(final ZonedDateTime scheduledTime, final String stationShortCode, final int stationUICCode) {
        final ZonedDateTime scheduledTimeUtc = scheduledTime.withZoneSameInstant(ZoneId.of("UTC"));
        final JourneyCompositionRow journeyCompositionRow = new JourneyCompositionRow(scheduledTimeUtc, stationShortCode, stationUICCode, "fi", TimeTableRow.TimeTableRowType.DEPARTURE);
        return new CompositionTimeTableRow(journeyCompositionRow);
    }
}
