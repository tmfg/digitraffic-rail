package fi.livi.rata.avoindata.updater.deserializers;

import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import fi.livi.rata.avoindata.updater.BaseTest;
import fi.livi.rata.avoindata.updater.config.HttpInputObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TimeTableRowDeserializerTest extends BaseTest {

    private static final int UIC_CODE = 1023;
    private static final LocalDateTime DEPARTURE_TIME = LocalDateTime.of(2014, 12, 9, 22, 14, 0);
    private static final LocalDateTime ESTIMATED_TIME = LocalDateTime.of(2014, 12, 9, 22, 15, 14);

    @Autowired
    private HttpInputObjectMapper httpInputObjectMapper;

    @SuppressWarnings("MessageMissingOnJUnitAssertion")
    @Test
    public void testDeserializer() throws Exception {
        final TimeTableRow timeTableRow = httpInputObjectMapper.readValue(new ClassPathResource("timetablerow.json").getFile(), TimeTableRow.class);

        assertEquals("HEK", timeTableRow.station.stationShortCode);
        assertEquals(UIC_CODE, timeTableRow.station.stationUICCode);
        assertEquals("FI", timeTableRow.station.countryCode);
        assertEquals(TimeTableRow.TimeTableRowType.DEPARTURE, timeTableRow.type);
        assertEquals("012", timeTableRow.commercialTrack);
        assertFalse(timeTableRow.cancelled);
        assertTrue(timeTableRow.commercialStop);

        assertEquals(DEPARTURE_TIME, timeTableRow.scheduledTime.toLocalDateTime());
        assertEquals(ESTIMATED_TIME, timeTableRow.liveEstimateTime.toLocalDateTime());
    }
}