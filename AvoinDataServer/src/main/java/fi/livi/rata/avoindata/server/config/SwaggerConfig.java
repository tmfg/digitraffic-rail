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
                .description("API for trains operating in Finland")
                .contact(new Contact("Google Groups", "http://groups.google.com/forum/#!forum/rata_digitraffic_fi", ""))
                .license("Creative Commons Nimeä 4.0")
                .licenseUrl("http://creativecommons.org/licenses/by/4.0/")
                .version("1.0")
                .build();
    }
}
