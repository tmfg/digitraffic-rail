package fi.livi.rata.avoindata.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

@Configuration
public class DebugContextRefreshListener {
    private List<String> printedProperties = Arrays.asList("spring.datasource.url", "spring.profiles.active","spring.datasource.driverClassName");
    private Logger log = LoggerFactory.getLogger(DebugContextRefreshListener.class);

    @Autowired
    private ApplicationContext applicationContext;

    @EventListener(ContextRefreshedEvent.class)
    private void printPropertyValues() {
        final Environment environment = applicationContext.getEnvironment();
        for (final String printedProperty : printedProperties) {
            log.info("Property {} = {}", printedProperty, environment.getProperty(printedProperty));
        }

        log.info("It is now {}", ZonedDateTime.now());
    }
}
