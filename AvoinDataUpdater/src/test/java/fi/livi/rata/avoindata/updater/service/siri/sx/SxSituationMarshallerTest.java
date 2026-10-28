package fi.livi.rata.avoindata.updater.service.siri.sx;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.updater.service.siri.common.DataFrameRef;
import fi.livi.rata.avoindata.updater.service.siri.common.LineId;
import fi.livi.rata.avoindata.updater.service.siri.common.OperatorRef;
import fi.livi.rata.avoindata.updater.service.siri.common.ServiceJourneyId;
import fi.livi.rata.avoindata.updater.service.siri.common.SiriWritingService;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxReportType;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxSituation;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxTarget;
import fi.livi.rata.avoindata.updater.service.siri.sx.model.SxText;

public class SxSituationMarshallerTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 27, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime START = NOW.minusHours(4);

    private final SiriWritingService writer = new SiriWritingService();
    private final SxSituationMarshaller marshaller = new SxSituationMarshaller(writer, "FTR");

    private static SxSituation situation(final String id, final boolean closed, final SxTarget target,
                                         final SxReportType reportType) {
        return new SxSituation("FTR:SituationNumber:" + id, 2, START.minusHours(1), START, closed, START,
                closed ? NOW.plusHours(4) : NOW.plusDays(1), reportType,
                List.of(new SxText("fi", "Lyhyt"), new SxText("sv", "Kort")),
                List.of(new SxText("fi", "Lyhyt teksti"), new SxText("sv", "Kort text")), target);
    }

    private String xml(final List<SxSituation> situations) {
        final byte[] bytes = marshaller.marshalToBytes(marshaller.marshal(situations, NOW));
        assertTrue(writer.isSchemaValid(bytes, "sx"), "XML is not valid against the SIRI 2.0 schema");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    public void stationAndTrainSituationsAreValidXml() {
        final String xml = xml(List.of(
                situation("SHM1", false, new SxTarget.StopPlaces(List.of("FSR:StopPlace:1", "FSR:StopPlace:10")),
                        SxReportType.GENERAL),
                situation("MVM1", true, new SxTarget.Journey(new ServiceJourneyId("FTR:ServiceJourney:145"),
                        new DataFrameRef("2026-10-27"), new OperatorRef("FTR:Operator:vr"), new LineId("FTR:Line:IC")),
                        SxReportType.INCIDENT)));

        assertTrue(xml.contains("<StopPlaceRef>FSR:StopPlace:10</StopPlaceRef>"));
        assertTrue(xml.contains("<Progress>closed</Progress>"));
        assertTrue(xml.contains("<ReportType>incident</ReportType>"));
        assertTrue(xml.contains("<SituationNumber>FTR:SituationNumber:SHM1</SituationNumber>"));
        assertTrue(xml.contains("xml:lang=\"SV\""));
    }

    @Test
    public void journeyWithoutOperatorAndLineIsValidXml() {
        xml(List.of(situation("MVM1", false, new SxTarget.Journey(new ServiceJourneyId("FTR:ServiceJourney:145"),
                new DataFrameRef("2026-10-27"), null, null), SxReportType.INCIDENT)));
    }

    @Test
    public void noSituationsIsValidXml() {
        xml(List.of());
    }

    @Test
    public void sameSituationsGiveSameBytes() {
        final List<SxSituation> situations = List.of(
                situation("SHM1", false, new SxTarget.StopPlaces(List.of("FSR:StopPlace:1")), SxReportType.GENERAL));

        assertArrayEquals(marshaller.marshalToBytes(marshaller.marshal(situations, NOW)),
                marshaller.marshalToBytes(marshaller.marshal(situations, NOW)));
    }
}
