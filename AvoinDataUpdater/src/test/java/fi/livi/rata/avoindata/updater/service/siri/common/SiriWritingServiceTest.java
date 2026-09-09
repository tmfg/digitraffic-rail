package fi.livi.rata.avoindata.updater.service.siri.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import uk.org.siri.siri21.Siri;

import static org.junit.jupiter.api.Assertions.*;

class SiriWritingServiceTest {

    private SiriWritingService writingService;

    @BeforeEach
    void setUp() {
        writingService = new SiriWritingService();
    }

    // --- WRITE-01: Empty envelope marshals to well-formed XML ---

    @Test
    void givenEmptyEnvelope_whenMarshalToXml_thenProducesWellFormedXml() {
        // given
        final ZonedDateTime now = ZonedDateTime.of(2026, 7, 10, 18, 0, 0, 0, ZoneOffset.UTC);
        final Siri envelope = writingService.buildEnvelope(now, "XXX");

        // when
        final String xml = writingService.marshalToXml(envelope);

        // then
        assertNotNull(xml);
        assertFalse(xml.isEmpty());
        assertTrue(xml.startsWith("<?xml"));
        assertTrue(xml.contains("<Siri"));
    }

    // --- WRITE-02: Envelope contains version="2.0" ---

    @Test
    void givenEnvelope_whenMarshalToXml_thenContainsVersion20() {
        // given
        final ZonedDateTime now = ZonedDateTime.of(2026, 7, 10, 18, 0, 0, 0, ZoneOffset.UTC);
        final Siri envelope = writingService.buildEnvelope(now, "XXX");

        // when
        final String xml = writingService.marshalToXml(envelope);

        // then
        assertTrue(xml.contains("version=\"2.0\""));
    }

    // --- WRITE-03: Envelope contains ServiceDelivery with ResponseTimestamp ---

    @Test
    void givenEnvelopeWithTimestamp_whenMarshalToXml_thenContainsResponseTimestamp() {
        // given — 18:00 UTC = 21:00 Helsinki summer
        final ZonedDateTime timestamp = ZonedDateTime.of(2026, 7, 10, 18, 0, 0, 0, ZoneOffset.UTC);
        final Siri envelope = writingService.buildEnvelope(timestamp, "XXX");

        // when
        final String xml = writingService.marshalToXml(envelope);

        // then
        assertTrue(xml.contains("<ResponseTimestamp>2026-07-10T21:00:00</ResponseTimestamp>"));
    }

    // --- WRITE-04: ProducerRef rendered from argument, never hardcoded ---

    @Test
    void givenProducerRefArgument_whenMarshalToXml_thenContainsProvidedProducerRef() {
        // given
        final ZonedDateTime now = ZonedDateTime.of(2026, 7, 10, 18, 0, 0, 0, ZoneOffset.UTC);
        final Siri envelope = writingService.buildEnvelope(now, "TEST");

        // when
        final String xml = writingService.marshalToXml(envelope);

        // then
        assertTrue(xml.contains("<ProducerRef>TEST</ProducerRef>"));
        assertFalse(xml.contains("<ProducerRef>DT</ProducerRef>"));
    }

    // --- WRITE-05: A complete SIRI-ET document passes XSD validation ---
    
    @Test
    void givenValidSiriEtDocument_whenValidateSchema_thenReturnsTrue() throws IOException {
        // given — a complete, schema-valid SIRI-ET document
        final byte[] bytes = getClass().getResourceAsStream("/siri/expected-siri-et-mixed.xml").readAllBytes();

        // when
        final boolean valid = writingService.isSchemaValid(bytes);

        // then
        assertTrue(valid);
    }

    // --- WRITE-06: Malformed XML fails validation ---

    @Test
    void givenInvalidXml_whenValidateSchema_thenReturnsFalse() {
        // given
        final byte[] invalidXml = "<notSiri/>".getBytes(StandardCharsets.UTF_8);

        // when
        final boolean valid = writingService.isSchemaValid(invalidXml);

        // then
        assertFalse(valid);
    }

    // --- WRITE-07: marshalToBytes produces UTF-8 encoded output ---

    @Test
    void givenEnvelope_whenMarshalToBytes_thenProducesUtf8EncodedXml() {
        // given
        final ZonedDateTime now = ZonedDateTime.of(2026, 7, 10, 18, 0, 0, 0, ZoneOffset.UTC);
        final Siri envelope = writingService.buildEnvelope(now, "XXX");

        // when
        final byte[] bytes = writingService.marshalToBytes(envelope);

        // then
        assertNotNull(bytes);
        final String xml = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(xml.contains("<?xml"));
        assertTrue(xml.contains("UTF-8"));
    }
}
