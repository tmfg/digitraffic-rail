package fi.livi.rata.avoindata.server.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RailApplicationConfiguration {
    // Database credentials are fetched from the AWS Secrets Manager
    @Bean
    @Primary
    public DataSource dataSource(final @Value("${url}") String url,
                                 final @Value("${username}") String username,
                                 final @Value("${password}") String password) {
        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create(); 
        dataSourceBuilder.url(url);
        dataSourceBuilder.username(username); 
        dataSourceBuilder.password(password); 
        return dataSourceBuilder.build();
    }
}
