package fi.livi.rata.avoindata.server.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    private final String host;
    private final String scheme;

    public SwaggerConfig(final @Value("${dt.domain.url:https://rata.digitraffic.fi}") String domainUrl) throws URISyntaxException {
        final URI uri = new URI(domainUrl);
        final int port = uri.getPort();
        host = port > -1 ? uri.getHost() + ":" + port : uri.getHost();
        scheme = uri.getScheme();
    }

    @Bean
    public GroupedOpenApi railApi() {
        return GroupedOpenApi.builder()
                .group("rail-api")
                .pathsToMatch("/api/v*/**")
                .addOpenApiCustomizer(openApiCustomizer())
                .build();
    }

    private OpenApiCustomizer openApiCustomizer() {
        return openApi -> {
            openApi.setInfo(new Info()
                    .title("rata.digitraffic.fi")
                    .version("1.0"));

            final Server server = new Server();
            server.setUrl(scheme + "://" + host);
            openApi.setServers(List.of(server));
        };
    }
}
