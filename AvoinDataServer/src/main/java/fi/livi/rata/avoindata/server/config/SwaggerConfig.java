package fi.livi.rata.avoindata.server.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.core.converter.ModelConverters;
import fi.livi.rata.avoindata.common.domain.lastupdated.UpToDateInformation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    private final String host;
    private final String scheme;

    public SwaggerConfig(final @Value("${dt.domain.url}") String domainUrl) throws URISyntaxException {
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

            addLastUpdated(openApi);
        };
    }

    /**
     * The endpoint is served by the updater application (not by this one), so it is added here
     * to the public API documentation. The schema is generated from the model shared with the updater.
     */
    private static void addLastUpdated(final OpenAPI openApi) {
        final Schema<?> information = ModelConverters.getInstance()
                .readAllAsResolvedSchema(UpToDateInformation.class).schema;

        final Schema<?> response = new ObjectSchema()
                .additionalProperties(information)
                .description("Keyed by data type, for example TRAINS, TRAIN_LOCATIONS, GTFS, SIRI_ET, SIRI_VM, SIRI_SX_STATIONS and SIRI_SX_TRAINS. "
                        + "A data type is listed only after it has been updated at least once since the service started.");

        final Operation operation = new Operation()
                .addTagsItem("last-updated")
                .summary("Returns the last update time of each data type")
                .description("Shows how fresh the data is and whether it is within the alarm limit.")
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse()
                        .description("OK")
                        .content(new Content().addMediaType("application/json", new MediaType().schema(response)))));

        openApi.path("/api/v1/last-updated", new PathItem().get(operation));
    }
}
