package fi.livi.rata.avoindata.updater.deserializers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import fi.finrail.koju.model.KokoonpanoDto;
import fi.livi.rata.avoindata.common.domain.composition.JourneyComposition;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.service.TrakediaLiikennepaikkaService;

public class JourneyCompositionConverterTest extends BaseTest {

    private static final Set<Integer> locations = new HashSet<>();

    @Autowired
    private JourneyCompositionConverter journeyCompositionConverter;

    @MockBean
    private TrakediaLiikennepaikkaService trakediaLiikennepaikkaService;

    @Test
    public void filterNewestVersions() throws Exception {
        final KokoonpanoDto[] all = testDataService.readJourneyCompositions("/koju/julkisetkokoonpanot/2024-11-13--9715.json");
        final ArrayList<KokoonpanoDto> newest = journeyCompositionConverter.filterNewestVersions(all);
        assertEquals(3, all.length);
        assertEquals(1, newest.size());
        assertEquals(19539509L, newest.getFirst().getMessageReference());
        assertEquals(Instant.parse("2024-11-13T05:48:46Z"), newest.getFirst().getMessageDateTime().toInstant());

    }

    @Sql({ "/koju/sql/base.sql", "/koju/sql/time_table_row-2024-11-13--9715.sql" })
    @Transactional
    @Test
    public void deserializePublicCompositions() throws Exception {
        testDataService.mockGetTrakediaLiikennepaikkaNodes(trakediaLiikennepaikkaService);

        final JourneyComposition journeyComposition = testDataService.deserializeSingleTrainJourneyCompositions().getFirst();

        assertEquals(((double) Instant.now().toEpochMilli()), (double) journeyComposition.version, 500); // 100 ms variation
        assertEquals(Instant.parse("2024-11-13T05:48:46Z"), journeyComposition.messageDateTime); // "messageDateTime": "2024-11-13T05:48:46Z"
        assertEquals("vr", journeyComposition.operator.operatorShortCode);
        assertEquals(9715, journeyComposition.trainNumber);
        assertEquals(LocalDate.of(2024, 11, 13), journeyComposition.departureDate);
        assertEquals(5L, journeyComposition.trainTypeId);
        assertEquals(2L, journeyComposition.trainCategoryId);
        assertEquals(217, journeyComposition.totalLength); // 217000 mm
        assertEquals(160, journeyComposition.maximumSpeed);

        assertEquals(ZonedDateTime.parse("2024-11-13T17:06:00Z"), journeyComposition.startStation.scheduledTime);
        assertEquals("HKI", journeyComposition.startStation.stationShortCode);
        assertEquals(1, journeyComposition.startStation.stationUICCode);
        assertEquals("FI", journeyComposition.startStation.countryCode);
        assertEquals(TimeTableRow.TimeTableRowType.DEPARTURE, journeyComposition.startStation.type);

        assertEquals(ZonedDateTime.parse("2024-11-13T18:01:00Z"), journeyComposition.endStation.scheduledTime);
        assertEquals("RI", journeyComposition.endStation.stationShortCode);
        assertEquals(40, journeyComposition.endStation.stationUICCode);
        assertEquals("FI", journeyComposition.endStation.countryCode);
        assertEquals(TimeTableRow.TimeTableRowType.ARRIVAL, journeyComposition.endStation.type);

        assertEquals(4, journeyComposition.wagons.size());
        for (final Wagon wagon : journeyComposition.wagons) {
            assertWagon(wagon);
        }

        assertEquals(4, journeyComposition.locomotives.size());
        for (final Locomotive locomotive : journeyComposition.locomotives) {
            assertLocomotive(locomotive);
        }
    }

    private static void assertLocomotive(final Locomotive locomotive) {
        if (locomotive.location == 1) {
            assertEquals("94106004026-3", locomotive.vehicleNumber);
        } else {
            assertEquals(13, locomotive.vehicleNumber.length());
        }

        assertEquals("Sm4", locomotive.locomotiveType);
        assertEquals("S", locomotive.powerTypeAbbreviation);
        assertNull(locomotive.powerType); // This is set later in CompositionService.createJourneySection
        assertNull(locomotive.journeysection); // This is set later in CompositionService.createJourneySection
    }

    private static void assertWagon(final Wagon wagon) {
        assertTrue(wagon.location > 0);
        assertFalse(locations.contains(wagon.location));
        locations.add(wagon.location);

        assertEquals(wagon.location, wagon.salesNumber);
        if (wagon.location == 1) {
            assertEquals("94106004026-3", wagon.vehicleNumber);
            assertEquals(5441, wagon.length);
        } else {
            assertEquals(13, wagon.vehicleNumber.length());
            assertEquals(5440, wagon.length);
        }
        assertEquals(wagon.location == 1, falseIfNull(wagon.playground));
        assertEquals(wagon.location == 2, falseIfNull(wagon.pet));
        assertEquals(wagon.location == 1, falseIfNull(wagon.catering));
        assertEquals(wagon.location == 2, falseIfNull(wagon.video));
        assertEquals(wagon.location == 1, falseIfNull(wagon.luggage));
        assertEquals(wagon.location == 2, falseIfNull(wagon.smoking));
        assertEquals(wagon.location == 1, falseIfNull(wagon.disabled));
    }

    private static boolean falseIfNull(final Boolean maybe) {
        return maybe != null && maybe;
    }
}