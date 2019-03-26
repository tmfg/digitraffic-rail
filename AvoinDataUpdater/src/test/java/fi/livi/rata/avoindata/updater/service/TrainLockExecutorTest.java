package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.updater.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class TrainLockExecutorTest extends BaseTest {
    public Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TrainLockExecutor trainLockExecutor;

    @Test
    public void singleThreadLockingShouldWork() {
        List<Integer> order = new ArrayList<>();

        trainLockExecutor.executeInLock(() -> {
            Thread.sleep(400);
            order.add(10);

            return new Object();
        });

        trainLockExecutor.executeInLock(() -> {
            Thread.sleep(300);
            order.add(9);

            return new Object();
        });

        trainLockExecutor.executeInLock(() -> {
            Thread.sleep(200);
            order.add(8);

            return new Object();
        });

        trainLockExecutor.executeInLock(() -> {
            Thread.sleep(100);
            order.add(7);

            return new Object();
        });

        Assert.assertEquals("1", order.get(0), new Integer(10));
        Assert.assertEquals("2", order.get(1), new Integer(9));
        Assert.assertEquals("3", order.get(2), new Integer(8));
        Assert.assertEquals("4", order.get(3), new Integer(7));
    }

    @Test
    public void multiThreadLockingShouldWork() throws InterruptedException {
        List<Integer> order = new ArrayList<>();

        final ExecutorService executorService = Executors.newFixedThreadPool(4);

        executorService.submit(() -> {
            log.info("1 submitted");
            trainLockExecutor.executeInLock(() -> {
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
            trainLockExecutor.executeInLock(() -> {
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
            trainLockExecutor.executeInLock(() -> {
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
            trainLockExecutor.executeInLock(() -> {
                log.info("4 started");

                Thread.sleep(100);
                order.add(7);

                log.info("4 ended");
                return new Object();
            });
        });

        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        Assert.assertEquals("1", order.get(0), new Integer(10));
        Assert.assertEquals("2", order.get(1), new Integer(9));
        Assert.assertEquals("3", order.get(2), new Integer(8));
        Assert.assertEquals("4", order.get(3), new Integer(7));
    }
}
