package fi.livi.rata.avoindata.updater.config;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import com.google.common.base.Joiner;

@Configuration(value = "DebugContextRefreshListener_AvoindataUpdater")
public class DebugContextRefreshListener {
    private List<String> printedProperties = Arrays.asList("spring.datasource.url", "spring.profiles.active", "updater.liikeinterface-url", "updater.mqtt.server-url", "spring.datasource.driverClassName");
    private Logger log = LoggerFactory.getLogger(DebugContextRefreshListener.class);

    @Autowired
    Environment environment;

    @EventListener(ContextRefreshedEvent.class)
    private void printPropertyValues() {
        log.info("Properties: {}", Joiner.on(",").join(printedProperties.stream().map(s -> String.format("%s = %s", s, environment.getProperty(s))).collect(Collectors.toList())));
        log.info("It is now {}", ZonedDateTime.now());
    }
}
