package fi.livi.rata.avoindata.server.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    private final MessageService messageService;
    private final String host;
    private final String scheme;

    public SwaggerConfig(final MessageService messageService,
                         final @Value("${dt.domain.url:https://rata.digitraffic.fi}") String domainUrl) throws URISyntaxException {
        this.messageService = messageService;

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
                    .title(messageService.getMessage("apiInfo.title"))
                    .description(messageService.getMessage("apiInfo.description"))
                    .version("1.0")
                    .contact(new Contact()
                            .name(messageService.getMessage("apiInfo.contact.name"))
                            .url(messageService.getMessage("apiInfo.contact.url")))
                    .termsOfService(messageService.getMessage("apiInfo.terms.of.service"))
                    .license(new License()
                            .name(messageService.getMessage("apiInfo.licence"))
                            .identifier(messageService.getMessage("apiInfo.licence.identifier"))
                            .url(messageService.getMessage("apiInfo.licence.url"))));

            final Server server = new Server();
            server.setUrl(scheme + "://" + host);
            openApi.setServers(List.of(server));
        };
    }
}
