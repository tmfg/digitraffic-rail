package fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Collection;

import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.LiikeInterface.MockMvcBaseTest;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.Junapaiva;

@Ignore
public class JunapaivaControllerTest extends MockMvcBaseTest {
    private static final LocalDate TEST_DATE = LocalDate.of(2014, 10, 26);

    @Autowired
    private JunapaivaController junapaivaController;

    @Test
    public void testGetJunapaivas() throws Exception {
        final Collection<Junapaiva> junapaivas = junapaivaController.getByDate(TEST_DATE);

        assertEquals(15, junapaivas.size());
        for (Junapaiva junapaiva : junapaivas) {
            assertNotNull(junapaiva.id);
            assertNotNull(junapaiva.id.junanumero);
            assertNotNull(junapaiva.id.lahtopvm);

            assertNotNull(junapaiva.aikataulu);
            assertNotNull(junapaiva.jupaTapahtumas);
            assertTrue(String.format(junapaiva.id.junanumero), junapaiva.jupaTapahtumas.stream()
                    .filter(x -> x.kaupallinen != null).findAny().isPresent());
        }
    }

    @Test
    public void testGetJunapaivas2() throws Exception {
        final String path = String.format("/avoin/trains?date=%s", TEST_DATE);
        assertLength(path,15);
    }
}
