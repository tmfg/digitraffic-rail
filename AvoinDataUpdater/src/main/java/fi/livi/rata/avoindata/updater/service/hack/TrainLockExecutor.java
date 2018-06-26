package fi.livi.rata.avoindata.updater.service.hack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class TrainLockExecutor {
    private Logger log = LoggerFactory.getLogger(TrainLockExecutor.class);

    final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public <T> T executeInLock(Callable<T> callable) {
        final Future<T> future = executorService.submit(callable);

        try {
            return future.get();
        } catch (Exception e) {
            log.error("Error executing callable in TrainLockExecutor", e);
            return null;
        }
    }
}
