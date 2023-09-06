package fi.livi.rata.avoindata.server.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class RailApplicationConfiguration {

    @Autowired
    private Environment env;

    @Bean
    @Primary
    public DataSource dataSource(
            @Autowired(required = false)
            final AWSDataSourceCredentials awsDataSourceCredentials,
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

        final String actualUsername = awsDataSourceCredentials != null ? awsDataSourceCredentials.getUsername() : username;
        final String actualPassword = awsDataSourceCredentials != null ? awsDataSourceCredentials.getPassword() : password;

        final HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(actualUsername);
        dataSource.setPassword(actualPassword);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setLeakDetectionThreshold(leakDetectionThreshold);
        dataSource.setReadOnly(readOnly);

        return dataSource;
    }
}
