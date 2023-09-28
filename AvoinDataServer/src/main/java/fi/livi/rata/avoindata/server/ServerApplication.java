package fi.livi.rata.avoindata.server;

import fi.livi.rata.avoindata.common.dao.CustomGeneralRepositoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.TimeZone;

@Configuration
@EnableAutoConfiguration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan(basePackages = {"fi.livi.rata.avoindata.server", "fi.livi.rata.avoindata.common"})
@EntityScan(basePackages = "fi.livi.rata.avoindata.common.domain")
@EnableJpaRepositories(basePackages = "fi.livi.rata.avoindata.common.dao", repositoryBaseClass = CustomGeneralRepositoryImpl.class)
@EnableScheduling
@EnableCaching
public class ServerApplication {
    private static Logger log = LoggerFactory.getLogger(ServerApplication.class);

    public static void main(final String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Etc/UTC"));

        final SpringApplication application = createApplication();
        application.run(args);
    }

    private static SpringApplication createApplication() {
        final SpringApplication application = new SpringApplication(ServerApplication.class);
        final Properties properties = new Properties();

        properties.put("myHostname", getHostname());
        application.setDefaultProperties(properties);

        return application;
    }

    private static String getHostname() {
        try {
            final InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName();
        } catch (final UnknownHostException ex) {
            return "unknown";
        }
    }
}
