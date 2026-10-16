package fi.livi.rata.avoindata.updater.service.stopsector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class MissingSectorCounterTest {

    @Test
    void drainMissingSectorsReturnsCountsAndClearsTheCounter() {
        final MissingSectorCounter counter = new MissingSectorCounter();

        counter.addMissingSector("A");
        counter.addMissingSector("A");
        counter.addMissingSector("B");

        final Map<String, Integer> drained = counter.drainMissingSectors();

        assertEquals(2, drained.get("A"));
        assertEquals(1, drained.get("B"));
        assertTrue(counter.drainMissingSectors().isEmpty());
    }
}

