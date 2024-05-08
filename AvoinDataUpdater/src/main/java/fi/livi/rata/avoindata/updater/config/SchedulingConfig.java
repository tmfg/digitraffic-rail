package fi.livi.rata.avoindata.updater.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

@ConditionalOnProperty(
        value = "app.scheduling.enable", havingValue = "true", matchIfMissing = true
)
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public void configureTasks(final ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService taskExecutor() {
        final ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setUncaughtExceptionHandler((t, e) -> log.error(t.getName() + ": Unhandled exception", e))
                .setNameFormat("scheduled-%d")
                .build();

        return Executors.newScheduledThreadPool(15, namedThreadFactory);
    }
}