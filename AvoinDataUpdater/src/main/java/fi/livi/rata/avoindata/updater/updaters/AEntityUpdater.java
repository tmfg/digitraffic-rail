package fi.livi.rata.avoindata.updater.updaters;

import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.function.Consumer;

public abstract class AEntityUpdater<T> {
    protected static final Logger log = LoggerFactory.getLogger(AEntityUpdater.class);

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected WebClient ripaWebClient;

    @Autowired
    private LastUpdateService lastUpdateService;

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
        new SimpleAsyncTaskExecutor().execute(this::wrapUpdate);
    }

    protected <T> T getForObjectWithRetry(final String path, final Class<T> responseType) {
        return retryTemplate.execute(context -> {
            log.info("Requesting data from " + path);

            return ripaWebClient.get().uri(path)
                    .retrieve().bodyToMono(responseType).block();
        });
    }

    private void wrapUpdate() {
        update();
    }

    protected abstract void update();

    protected final void doUpdate(final String path, final Consumer<T> updater, final Class<T> responseType) {
        if (StringUtils.isEmpty(liikeInterfaceUrl)) {
            return;
        }

        final T results = getForObjectWithRetry(path, responseType);
        persist(path, updater, results);
    }

    protected final void persist(final String path, final Consumer<T> updater, final T results) {
        updater.accept(results);
        log.info(String.format("Updated %s", path));

        lastUpdateService.update(path);
    }
}
