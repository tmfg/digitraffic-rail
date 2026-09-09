package fi.livi.rata.avoindata.updater.service.siri.common;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class SiriWritingServiceValidationTest {

    private final SiriWritingService service = new SiriWritingService();

    // Our SIRI-ET output validates against the true SIRI 2.0 XML Schema.
    @Test
    void givenSiri20Output_whenValidate_thenValid() throws IOException {
        assertTrue(service.isSchemaValid(golden()));
    }

    // A 2.1-only element (DepartureStatus on a RecordedCall) is rejected by the 2.0 schema.
    @Test
    void givenSiri21OnlyElementOnRecordedCall_whenValidate_thenInvalid() throws IOException {
        final String valid = new String(golden(), UTF_8);
        // DepartureStatus sits (per 2.1 order) right after ActualDepartureTime — schema-correct position, so it
        // is rejected only because RecordedCall has no DepartureStatus in SIRI 2.0.
        final String with21 = valid.replace(
                "<ActualDepartureTime>2026-07-15T08:01:00</ActualDepartureTime>",
                "<ActualDepartureTime>2026-07-15T08:01:00</ActualDepartureTime>"
                        + "<DepartureStatus>onTime</DepartureStatus>");
        assertNotEquals(valid, with21, "injection should have modified the document");
        assertFalse(service.isSchemaValid(with21.getBytes(UTF_8)));
    }

    private byte[] golden() throws IOException {
        return getClass().getResourceAsStream("/siri/expected-siri-et-mixed.xml").readAllBytes();
    }
}
