package fi.livi.rata.avoindata.updater.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import fi.livi.rata.avoindata.updater.BaseTest;


public class TrainLockExecutorTest extends BaseTest {
    public Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Test
    public void singleThreadLockingShouldWork() {
        final List<Integer> order = new ArrayList<>();

        trainLockExecutor.executeInLock("test", () -> {
            Thread.sleep(400);
            order.add(10);

            return new Object();
        });

        trainLockExecutor.executeInLock("test", () -> {
            Thread.sleep(300);
            order.add(9);

            return new Object();
        });

        trainLockExecutor.executeInLock("test", () -> {
            Thread.sleep(200);
            order.add(8);

            return new Object();
        });

        trainLockExecutor.executeInLock("test", () -> {
            Thread.sleep(100);
            order.add(7);

            return new Object();
        });

        Assertions.assertEquals(order.get(0), Integer.valueOf(10), "1");
        Assertions.assertEquals(order.get(1), Integer.valueOf(9), "2");
        Assertions.assertEquals(order.get(2), Integer.valueOf(8), "3");
        Assertions.assertEquals(order.get(3), Integer.valueOf(7), "4");
    }

    @Test
    public void multiThreadLockingShouldWork() throws InterruptedException {
        final List<Integer> order = new ArrayList<>();

        final ExecutorService executorService = Executors.newFixedThreadPool(4);

        executorService.submit(() -> {
            log.info("1 submitted");
            trainLockExecutor.executeInLock("test", () -> {
                log.info("1 started");

                Thread.sleep(400);
                order.add(10);

                log.info("1 ended");

                return new Object();
            });
        });

        Thread.sleep(20);

        executorService.execute(() -> {
            log.info("2 submitted");
            trainLockExecutor.executeInLock("test", () -> {
                log.info("2 started");

                Thread.sleep(300);
                order.add(9);

                log.info("2 ended");

                return new Object();
            });
        });

        Thread.sleep(20);

        executorService.execute(() -> {
            log.info("3 submitted");
            trainLockExecutor.executeInLock("test", () -> {
                log.info("3 started");

                Thread.sleep(200);
                order.add(8);

                log.info("3 ended");

                return new Object();
            });
        });

        Thread.sleep(20);

        executorService.execute(() -> {
            log.info("4 submitted");
            trainLockExecutor.executeInLock("test", () -> {
                log.info("4 started");

                Thread.sleep(100);
                order.add(7);

                log.info("4 ended");
                return new Object();
            });
        });

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        Assertions.assertEquals(order.get(0), Integer.valueOf(10), "1");
        Assertions.assertEquals(order.get(1), Integer.valueOf(9), "2");
        Assertions.assertEquals(order.get(2), Integer.valueOf(8), "3");
        Assertions.assertEquals(order.get(3), Integer.valueOf(7), "4");
    }
}
