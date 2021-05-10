package fi.livi.rata.avoindata.updater.updaters;

import java.util.function.Consumer;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import fi.livi.rata.avoindata.updater.service.isuptodate.LastUpdateService;

public abstract class AEntityUpdater<T> {
    protected static final Logger log = LoggerFactory.getLogger(AEntityUpdater.class);

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RestTemplate restTemplate;

    @Autowired
    private LastUpdateService lastUpdateService;

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
        new SimpleAsyncTaskExecutor().execute(this::wrapUpdate);
    }

    protected <T> T getForObjectWithRetry(final String targetUrl, final Class<T> responseType) {
        return retryTemplate.execute(context -> {
            log.info("Requesting data from " + targetUrl);
            return restTemplate.getForObject(targetUrl, responseType);
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

        final String targetUrl = String.format("%s/%s", liikeInterfaceUrl, path);
        final T results = getForObjectWithRetry(targetUrl, responseType);
        persist(path, updater, results);
    }

    protected final void persist(String path, Consumer<T> updater, T results) {
        updater.accept(results);
        log.info(String.format("Updated %s", path));

        lastUpdateService.update(path);
    }
}
