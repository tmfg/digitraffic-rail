package fi.livi.rata.avoindata.server;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class HikariPoolStatistics {
    private final HikariDataSource hikariDataSource;
    private final AtomicInteger allActiveCounter = new AtomicInteger(0);

    private static final Logger log = LoggerFactory.getLogger(HikariPoolStatistics.class);

    private static final int ALL_ACTIVE_THRESHOLD = 120; // seconds

    public HikariPoolStatistics(final HikariDataSource hikariDataSource) {
        this.hikariDataSource = hikariDataSource;
    }

    @Scheduled(fixedRate = 1000)
    public void checkStats() {
        final var poolBean = hikariDataSource.getHikariPoolMXBean();
        final int activeConnections = poolBean.getActiveConnections();
        final int totalConnections = poolBean.getTotalConnections();

        if(activeConnections == totalConnections) {
            final var activeCount = allActiveCounter.incrementAndGet();
            log.info("All connections active for {} seconds", activeCount);

            if(activeCount >= ALL_ACTIVE_THRESHOLD) {
                log.error("All connections have been active for {} seconds!", activeCount);

                allActiveCounter.set(0);
            }
        } else {
            allActiveCounter.set(0);
        }
    }
}
