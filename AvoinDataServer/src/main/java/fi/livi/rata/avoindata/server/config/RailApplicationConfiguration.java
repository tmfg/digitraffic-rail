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
    public DataSource dataSource(@Autowired(required = false) AWSDataSourceCredentials awsDataSourceCredentials) {
        String url = awsDataSourceCredentials != null ? awsDataSourceCredentials.getUrl() : env.getProperty("spring.datasource.url");
        String username = awsDataSourceCredentials != null ? awsDataSourceCredentials.getUsername() : env.getProperty("spring.datasource.username");
        String password = awsDataSourceCredentials != null ? awsDataSourceCredentials.getPassword() : env.getProperty("spring.datasource.password");

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create(); 
        dataSourceBuilder.url(url);
        dataSourceBuilder.username(username); 
        dataSourceBuilder.password(password); 
        return dataSourceBuilder.build();
    }
}
