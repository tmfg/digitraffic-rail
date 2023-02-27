package fi.livi.rata.avoindata.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("aws")
public class AWSDataSourceCredentials {
    final private String username;
    final private String password;

    // Database credentials are fetched from the AWS Secrets Manager
    AWSDataSourceCredentials(final @Value("${username}") String username,
                             final @Value("${password}") String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
}
