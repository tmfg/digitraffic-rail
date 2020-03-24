package fi.livi.rata.avoindata.updater.service.ruma;

import org.junit.Test;

import java.util.Random;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RumaUtilsTest {

    private static final Random random = new Random(System.nanoTime());

    @Test
    public void oldInfraOidIsConvertedToNew() {
        assertEquals("1.2.246.586.1.11.104373", normalizeTrakediaInfraOid("x.x.xxx.LIVI.INFRA.11.104373"));
    }

    @Test
    public void newInfraOidIsRetained() {
        assertEquals("1.2.246.586.1.11.104373", normalizeTrakediaInfraOid("1.2.246.586.1.11.104373"));
    }

    @Test
    public void nullInfraOidReturnsNull() {
        assertNull(normalizeTrakediaInfraOid(null));
    }

    @Test
    public void oldJetiOidIsConvertedToNew() {
        assertEquals("1.2.246.586.2.81.104372", normalizeJetiOid("1.2.246.LIVI.ETJ2.81.104372"));
    }

    @Test
    public void newJetiOidIsRetained() {
        assertEquals("1.2.246.586.2.81.104372", normalizeJetiOid("1.2.246.586.2.81.104372"));
    }

    @Test
    public void nullJetiOidReturnsNull() {
        assertNull(normalizeJetiOid(null));
    }

    @Test
    public void rkmvaliToString() {
        final String ratanumero = "001";
        final int alkuRatakm = 50;
        final int alkuEtaisyys = 534;
        final int loppuRatakm = 304;
        final int loppuEtaisyys = 24;

        assertEquals("(001) 50+0534 > 304+0024", ratakmvaliToString(ratanumero, alkuRatakm, alkuEtaisyys, loppuRatakm, loppuEtaisyys));
    }
}
