package fi.livi.rata.avoindata.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
public class SwaggerConfig {
    @Bean
    public Docket swaggerSpringMvcPlugin() {
        return new Docket(DocumentationType.SWAGGER_2).apiInfo(apiInfo())
                .useDefaultResponseMessages(false)
                .select()
                .paths(PathSelectors.ant("/api/v*/**"))
                .build()   ;
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("rata.digitraffic.fi")
                .description(
                        "Digitraffic is a service operated by the [Fintraffic](https://www.fintraffic.fi) offering real time traffic information. \n" +
                                "Currently the service covers *road, marine and rail* traffic. More information can be found at the [Digitraffic website](https://www.digitraffic.fi/) \n\n\n" +
                                "The service has a public Google-group [rail.digitraffic.fi](https://groups.google.com/forum/#!forum/rata_digitraffic_fi) for \n" +
                                "communication between developers, service administrators and Fintraffic. \n" +
                                "The discussion in the forum is mostly in Finnish, but you're welcome to communicate in English too. \n\n\n" +
                                "### General notes of the API\n\n" +
                                "* Many Digitraffic APIs use GeoJSON as data format. Definition of the GeoJSON format can be found at https://tools.ietf.org/html/rfc7946.\n\n" +
                                "* For dates and times [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601) format is used with \"Zulu\" zero offset from UTC unless otherwise specified \n" +
                                "(i.e., \"yyyy-mm-ddThh:mm:ss[.mmm]Z\"). E.g. 2019-11-01T06:30:00Z.")
                .contact(new Contact("Digitraffic / Fintraffic", "https://www.digitraffic.fi/", ""))
                .license("Digitraffic is an open data service. All content from the service and the service documentation is licenced under the Creative Commons 4.0 BY license.")
                .licenseUrl("https://creativecommons.org/licenses/by/4.0/")
                .version("1.0")
                .build();
    }
}
