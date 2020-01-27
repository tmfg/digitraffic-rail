package fi.livi.rata.avoindata.updater.deserializers;

import org.junit.Test;

import static fi.livi.rata.avoindata.updater.deserializers.AEntityDeserializer.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AEntityDeserializerTest {

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
}
