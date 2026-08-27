package fi.livi.rata.avoindata.updater.service.siri.et;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.common.domain.common.TrainId;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTimeTableRow;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFSTrain;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiQuay;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStop;
import fi.livi.rata.avoindata.updater.service.netex.peti.PetiStopSource;
import fi.livi.rata.avoindata.updater.service.siri.common.ResolvedJourney;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriStopResolver;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriTimeConverter;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import uk.org.siri.siri21.Siri;

/**
 * Golden-master test: marshals a deterministic SIRI-ET document and compares it, node-for-node,
 * against a complete reference XML stored next to the test
 * ({@code src/test/resources/siri/expected-siri-et.xml}).
 *
 * <p>The reference file is the human-verified, profile-conformant expected output. If the SIRI-ET
 * mapping changes intentionally, regenerate it from {@code target/siri-et-actual.xml} (written on
 * every run) after confirming the new output is correct.
 */
class SiriEtGoldenXmlTest {

    private static final ZoneId HELSINKI = SiriTimeConverter.HELSINKI_ZONE;
    private static final LocalDate DEPARTURE_DATE = LocalDate.of(2026, 7, 15);
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, HELSINKI);
    private static final String PRODUCER_REF = "TEST";
    private static final String DATA_SOURCE = "FSR";
    private static final String GOLDEN_RESOURCE = "/siri/expected-siri-et.xml";

    private static final ResolvedJourney RESOLVED_59 =
            new ResolvedJourney("DT:ServiceJourney:59-12345", "2026-07-15", "DT:Line:IC", "DT:Operator:vr");

    private static final Map<String, Integer> UIC_MAP = Map.of(
            "HKI", 1,
            "TPE", 160,
            "OL", 280);

    private SiriEtService service;
    private SiriWritingService writingService;

    @BeforeEach
    void setUp() {
        final JourneyRefResolver journeyRefResolver = (trainNumber, date) ->
                trainNumber == 59L ? Optional.of(RESOLVED_59) : Optional.empty();

        final StationUicLookup stationUicLookup = shortCode -> {
            final Integer uic = UIC_MAP.get(shortCode);
            return uic != null ? OptionalInt.of(uic) : OptionalInt.empty();
        };

        final PetiStopSource petiStopSource = () -> List.of(
                new PetiStop("FSR:StopPlace:HKI", 1000001, "Helsinki", true, null,
                        List.of(new PetiQuay("FSR:Quay:HKI-7", "7", null))),
                new PetiStop("FSR:StopPlace:TPE", 1000160, "Tampere", true, null,
                        List.of(new PetiQuay("FSR:Quay:TPE-1", "1", null))),
                new PetiStop("FSR:StopPlace:OL", 1000280, "Oulu", true, null,
                        List.of(new PetiQuay("FSR:Quay:OL-1", "1", null))));

        writingService = new SiriWritingService();
        service = new SiriEtService(
                journeyRefResolver,
                stationUicLookup,
                new SiriStopResolver(petiStopSource),
                writingService,
                PRODUCER_REF,
                DATA_SOURCE);
    }

    @Test
    void generatedSiriEt_matchesGoldenFile() throws IOException {
        final Siri document = service.buildEtDocument(List.of(mixedRecordedAndEstimatedTrain()), NOW);
        final String actual = writingService.marshalToXml(document);

        // Always dump the actual output so a diff / regeneration is trivial when the mapping changes.
        final Path dump = Path.of("target", "siri-et-actual.xml");
        Files.createDirectories(dump.getParent());
        Files.writeString(dump, actual, StandardCharsets.UTF_8);

        final String expected = readGolden();
        assertEquals(normalizeXml(expected), normalizeXml(actual),
                "SIRI-ET output differs from " + GOLDEN_RESOURCE
                        + ". If the change is intentional, copy target/siri-et-actual.xml over the resource.");
    }

    /**
     * A representative, deterministic journey exercising the whole ET mapping: an already-departed
     * origin (RecordedCall), an intermediate stop that has arrived but departs late (RecordedCall
     * with an expected departure) and a still-upcoming, delayed terminus (EstimatedCall).
     */
    private GTFSTrain mixedRecordedAndEstimatedTrain() {
        final GTFSTrain train = new GTFSTrain();
        train.id = new TrainId(59L, DEPARTURE_DATE);
        train.cancelled = false;
        train.timeTableRows = new ArrayList<>();

        // HKI — origin, departed on time
        final GTFSTimeTableRow hkiDep = row("HKI", TimeTableRow.TimeTableRowType.DEPARTURE,
                at(8, 0), "7");
        hkiDep.actualTime = at(8, 1);
        train.timeTableRows.add(hkiDep);

        // TPE — arrived, departs 3 min late
        final GTFSTimeTableRow tpeArr = row("TPE", TimeTableRow.TimeTableRowType.ARRIVAL, at(9, 30), "1");
        tpeArr.actualTime = at(9, 33);
        train.timeTableRows.add(tpeArr);
        final GTFSTimeTableRow tpeDep = row("TPE", TimeTableRow.TimeTableRowType.DEPARTURE, at(9, 35), "1");
        tpeDep.liveEstimateTime = at(9, 38);
        train.timeTableRows.add(tpeDep);

        // OL — terminus, expected 6 min late
        final GTFSTimeTableRow olArr = row("OL", TimeTableRow.TimeTableRowType.ARRIVAL, at(14, 0), "1");
        olArr.liveEstimateTime = at(14, 6);
        train.timeTableRows.add(olArr);

        return train;
    }

    private GTFSTimeTableRow row(final String shortCode, final TimeTableRow.TimeTableRowType type,
            final ZonedDateTime scheduledTime, final String track) {
        final GTFSTimeTableRow r = new GTFSTimeTableRow();
        r.stationShortCode = shortCode;
        r.type = type;
        r.scheduledTime = scheduledTime;
        r.commercialTrack = track;
        r.commercialStop = true;
        return r;
    }

    private static ZonedDateTime at(final int hour, final int minute) {
        return ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, HELSINKI);
    }

    private static String readGolden() throws IOException {
        try (InputStream in = SiriEtGoldenXmlTest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            assertNotNull(in, "Missing golden resource " + GOLDEN_RESOURCE
                    + " — copy target/siri-et-actual.xml there to create it.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Collapse insignificant whitespace between elements so formatting differences don't fail the test. */
    private static String normalizeXml(final String xml) {
        return xml.replaceAll("(?s)>\\s+<", "><").trim();
    }
}
