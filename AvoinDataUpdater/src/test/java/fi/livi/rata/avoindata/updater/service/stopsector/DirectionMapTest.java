package fi.livi.rata.avoindata.updater.service.stopsector;

import fi.livi.rata.avoindata.updater.BaseTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.Rollback;

import java.util.List;

@Transactional
@Rollback
public class DirectionMapTest extends BaseTest {

    @Test
    public void empty() {
        final var map = new DirectionMap();

        Assertions.assertNull(map.getEntries("A"));
    }

    @Test
    public void directionNotFound() {
        final var map = new DirectionMap();

        Assertions.assertNull(map.getEntries("A"));
    }

    @Test
    public void south() {
        final var map = new DirectionMap();

        map.initialize(List.of(new DirectionMap.StopSectorDirection("A", "B", true)));

        Assertions.assertNotNull(map.getEntries("A"));
        Assertions.assertEquals(1, map.getEntries("A").size());
        Assertions.assertTrue(map.getEntries("A").get("B"));
    }

    @Test
    public void north() {
        final var map = new DirectionMap();

        map.initialize(List.of(new DirectionMap.StopSectorDirection( "A", "B", false)));

        Assertions.assertNotNull(map.getEntries("A"));
        Assertions.assertEquals(1, map.getEntries("A").size());
        Assertions.assertFalse(map.getEntries("A").get("B"));
    }

    @Test
    public void actualDirections() {
        final var map = new DirectionMap();
        map.initialize("sectors/directions.csv");

        Assertions.assertTrue(map.getEntries("TPE").get("LPÄ"));
        Assertions.assertTrue(map.getEntries("KEM").get("OL"));
        Assertions.assertFalse(map.getEntries("TPE").get("TSO"));
    }
}
