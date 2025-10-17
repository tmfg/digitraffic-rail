package fi.livi.rata.avoindata.server.config;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class RailApplicationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(RailApplicationConfiguration.class);

    @Autowired
    private Environment env;

    @Bean
    @Primary
    public DataSource dataSource(
            @Value("${spring.datasource.url}")
            final String url,
            @Value("${spring.datasource.username}")
            final String username,
            @Value("${spring.datasource.password}")
            final String password,
            @Value("${spring.datasource.driver-class-name}")
            final String driverClassName,
            @Value("${spring.datasource.hikari.maximum-pool-size}")
            final int maximumPoolSize,
            @Value("${spring.datasource.hikari.max-lifetime}")
            final int maxLifetime,
            @Value("${spring.datasource.hikari.idle-timeout}")
            final int idleTimeout,
            @Value("${spring.datasource.hikari.connection-timeout}")
            final int connectionTimeout,
            @Value("${spring.datasource.hikari.leak-detection-threshold}")
            final int leakDetectionThreshold,
            @Value("${spring.datasource.hikari.read-only}")
            final boolean readOnly
    ) {

        log.info("method=dataSource url {} driver {} maximumPoolSize {}",
                url, StringUtils.isNotBlank(driverClassName) ? driverClassName : "default", maximumPoolSize);

        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMaxLifetime(maxLifetime);
        config.setIdleTimeout(idleTimeout);
        config.setConnectionTimeout(connectionTimeout);
        config.setLeakDetectionThreshold(leakDetectionThreshold);
        config.setReadOnly(readOnly);
        config.setPoolName("application_pool");
        // register mbeans for debug
        config.setRegisterMbeans(true);

        log.info("method=dataSource url {} driver {} maximumPoolSize {}",
                url, StringUtils.isNotBlank(driverClassName) ? driverClassName : "default", maximumPoolSize);


        return new HikariDataSource(config);
    }
}
