package fi.livi.rata.avoindata.server.config;

import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfiguration {
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        // allow decoded slash
        return factory -> factory.addConnectorCustomizers(
                connector -> connector.setEncodedSolidusHandling(EncodedSolidusHandling.DECODE.getValue()));
    }
}
