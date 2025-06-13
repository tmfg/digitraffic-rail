package fi.livi.rata.avoindata.updater.service.stopmonitoring;

import fi.livi.rata.avoindata.common.domain.stopmonitoring.UdotData;
import fi.livi.rata.avoindata.common.domain.train.TimeTableRow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UdotUpdaterTest {
    private void testWith(final Boolean rowValue, final boolean ud, final boolean ut, final Boolean expectedUd, final Boolean expectedUt) {
        final UdotData udot = mock(UdotData.class);
        final TimeTableRow row = mock(TimeTableRow.class);

        row.unknownDelay = rowValue;
        row.unknownTrack = rowValue;

        when(udot.getUnknownDelay()).thenReturn(ud);
        when(udot.getUnknownTrack()).thenReturn(ut);

        UdotUpdater.updateUdotInformation(udot, row);

        Assertions.assertEquals(expectedUd, row.unknownDelay);
        Assertions.assertEquals(expectedUt, row.unknownTrack);
    }
    @Test
    public void testWithNullsInRow() {
        testWith(null, false, false, null, null);
        testWith(null, false, true, null, true);
        testWith(null, true, false, true, null);
        testWith(null, true, true, true, true);
    }

    @Test
    public void testWithFalsesInRow() {
        testWith(Boolean.FALSE, false, false, false, false);
        testWith(Boolean.FALSE, false, true, false, true);
        testWith(Boolean.FALSE, true, false, true, false);
        testWith(Boolean.FALSE, true, true, true, true);
    }

    @Test
    public void testWithTruesInRow() {
        testWith(Boolean.TRUE, false, false, false, false);
        testWith(Boolean.TRUE, false, true, false, true);
        testWith(Boolean.TRUE, true, false, true, false);
        testWith(Boolean.TRUE, true, true, true, true);
    }

}
