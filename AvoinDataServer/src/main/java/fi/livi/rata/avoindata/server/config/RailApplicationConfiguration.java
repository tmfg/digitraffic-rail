package fi.livi.rata.avoindata.server.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@Configuration
public class RailApplicationConfiguration {

    @Autowired
    private Environment env;

    @Bean
    @Primary
    public DataSource dataSource(@Autowired(required = false) final AWSDataSourceCredentials awsDataSourceCredentials) {
        final String url = env.getProperty("spring.datasource.url");
        final String driverClassName = env.getProperty("spring.datasource.driver-class-name");
        final String username = awsDataSourceCredentials != null ? awsDataSourceCredentials.getUsername() : env.getProperty("spring.datasource.username");
        final String password = awsDataSourceCredentials != null ? awsDataSourceCredentials.getPassword() : env.getProperty("spring.datasource.password");

        final DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        dataSourceBuilder.url(url);
        dataSourceBuilder.username(username); 
        dataSourceBuilder.password(password); 
        dataSourceBuilder.driverClassName(driverClassName);
        return dataSourceBuilder.build();
    }
}
