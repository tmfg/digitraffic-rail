package fi.livi.rata.avoindata.server.config;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class DNSConfig {
    @PostConstruct
    public void setTTL() {
        java.security.Security.setProperty("networkaddress.cache.ttl", "0");
    }
}
