package fi.livi.rata.avoindata.updater.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;

import com.google.common.base.Joiner;

@Configuration(value = "DebugContextRefreshListener_AvoindataUpdater")
public class PropertyLogger {
    private Logger log = LoggerFactory.getLogger(PropertyLogger.class);

    @Autowired
    private Environment environment;

    @EventListener(ContextRefreshedEvent.class)
    private void printPropertyValues() {
        final MutablePropertySources sources = ((AbstractEnvironment) environment).getPropertySources();
        final List<String> propertyKeys = StreamSupport.stream(sources.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .distinct()
                .filter(PropertyLogger::isSafeToPrint)
                .sorted().collect(Collectors.toList());

        log.info("Properties: {}", Joiner.on(", ").join(propertyKeys.stream().map(s -> String.format("%s = %s", s, environment.getProperty(s))).collect(Collectors.toList())));
    }

    private static boolean isSafeToPrint(final String prop) {
        return !(prop.contains("credentials") || prop.contains("password") || prop.contains("username") || prop.contains("api-key"));
    }
}
