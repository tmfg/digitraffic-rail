package fi.livi.rata.avoindata.updater.updaters;

import java.time.ZonedDateTime;
import java.util.function.Consumer;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.StringUtils;

import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import fi.livi.rata.avoindata.updater.config.SchedulingConfig;
import fi.livi.rata.avoindata.updater.service.RipaService;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import jakarta.annotation.PostConstruct;

import static fi.livi.rata.avoindata.updater.updaters.UpdateLogger.logUpdate;

public abstract class AEntityUpdater<T> {
    protected static final Logger log = LoggerFactory.getLogger(AEntityUpdater.class);

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RipaService ripaService;

    @Autowired
    private LastUpdateService lastUpdateService;

    @PostConstruct
    private void init() {
        if (!SchedulingConfig.isSchedulingEnabled()) {
            return;
        }

        try (final SimpleAsyncTaskExecutor e = new SimpleAsyncTaskExecutor()) {
            e.execute(this::wrapUpdate);
        } catch (final Exception e) {
            log.error("method=init failed to run async task wrapUpdate with error {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    protected T getForObjectWithRetry(final String path, final Class<T> responseType) {
        return retryTemplate.execute(context -> ripaService.getFromRipaRestTemplate(path, responseType));
    }

    private void wrapUpdate() {
        update();
    }

    protected abstract void update();

    protected final void doUpdate(final String path, final Consumer<T> updater, final Class<T> responseType) {
        if (!StringUtils.hasText(liikeInterfaceUrl)) {
            return;
        }

        final ZonedDateTime start = DateProvider.nowInHelsinki();

        final T results = getForObjectWithRetry(path, responseType);
        persist(path, updater, results, start);
    }

    protected final void persist(final String path, final Consumer<T> updater, final T results, ZonedDateTime start) {
        final ZonedDateTime middle = DateProvider.nowInHelsinki();
        updater.accept(results);

        final ZonedDateTime end = DateProvider.nowInHelsinki();
        final var count = results.getClass().isArray() ? ((Object[])results).length : 1;

        logUpdate(end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli(), path, count, middle.toInstant().toEpochMilli() - start.toInstant().toEpochMilli());

        lastUpdateService.update(path);
    }
}
