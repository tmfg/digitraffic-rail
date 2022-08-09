package fi.livi.rata.avoindata.server.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class SwaggerConfig {

    @Value("${dt.domain.url:https://rata.digitraffic.fi}")
    private String dtDomainUrl;

    @Bean
    public OpenAPI openApiConfig() {
        final OpenAPI openAPI = new OpenAPI().info(new Info().title("rata.digitraffic.fi")
                .description(apiDescription())
                .version("1.0")
                .contact(new Contact()
                        .name("Digitraffic / Fintraffic")
                        .url("https://www.digitraffic.fi/"))
                .license(new License()
                        .name("European Union Public License 1.2")
                        .identifier("EUPL-1.2")
                        .url("https://www.eupl.eu/1.2/en"))
        );
        final Server server = new Server();
        server.setUrl(dtDomainUrl);
        openAPI.setServers(List.of(server));
        return openAPI;
    }

    private String apiDescription() {
        return "Digitraffic is a service operated by the [Fintraffic](https://www.fintraffic.fi) offering real time traffic information. \n" +
                "Currently the service covers *road, marine and rail* traffic. More information can be found at the [Digitraffic website](https://www.digitraffic.fi/) \n\n\n" +
                "The service has a public Google-group [rail.digitraffic.fi](https://groups.google.com/forum/#!forum/rata_digitraffic_fi) for \n" +
                "communication between developers, service administrators and Fintraffic. \n" +
                "The discussion in the forum is mostly in Finnish, but you're welcome to communicate in English too. \n\n\n" +
                "### General notes of the API\n\n" +
                "* Many Digitraffic APIs use GeoJSON as data format. Definition of the GeoJSON format can be found at https://tools.ietf.org/html/rfc7946.\n\n" +
                "* For dates and times [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601) format is used with \"Zulu\" zero offset from UTC unless otherwise specified \n" +
                "(i.e., \"yyyy-mm-ddThh:mm:ss[.mmm]Z\"). E.g. 2019-11-01T06:30:00Z.";
    }
}
