package fi.livi.rata.avoindata.updater.updaters;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.spring.aop.XRayTraced;
import fi.livi.rata.avoindata.updater.config.InitializerRetryTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.function.Consumer;

public abstract class AEntityUpdater<T> implements XRayTraced {
    protected static final Logger log = LoggerFactory.getLogger(AEntityUpdater.class);

    @Value("${updater.liikeinterface-url}")
    protected String liikeInterfaceUrl;

    @Autowired
    protected InitializerRetryTemplate retryTemplate;

    @Autowired
    protected RestTemplate restTemplate;

    @PostConstruct
    private void init() {
        retryTemplate.setLogger(log);
        new SimpleAsyncTaskExecutor().execute(this::update);
    }

    protected T getForObjectWithRetry(final String targetUrl, final Class<T> responseType) {
        return retryTemplate.execute(context -> {
            log.info("Requesting data from " + targetUrl);
            return restTemplate.getForObject(targetUrl, responseType);
        });
    }

    protected abstract void update();

    protected final void doUpdate(final String path, final Consumer<T> updater, final Class<T> responseType) {
        AWSXRay.createSegment(this.getClass().getName(), (subsegment) -> {

            if (StringUtils.isEmpty(liikeInterfaceUrl)) {
                return;
            }

            final String targetUrl = String.format("%s/%s", liikeInterfaceUrl, path);
            final T results = getForObjectWithRetry(targetUrl, responseType);
            updater.accept(results);
            log.info(String.format("Updated %s", path));
        });
    }
}
