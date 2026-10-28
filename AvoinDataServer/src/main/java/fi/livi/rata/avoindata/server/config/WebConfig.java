package fi.livi.rata.avoindata.server.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    public static final String CONTEXT_PATH = "/api/v1/";

    private final DatabaseInitializationInterceptor databaseInitializionInterceptor;
    private final ExecuteTimeInterceptor executeTimeInterceptor;
    private final ParameterValidationInterceptor parameterValidationInterceptor;
    private final ContentTypeInterceptor contentTypeInterceptor;
    private final OSIVInterceptor osivInterceptor;

    public WebConfig(final DatabaseInitializationInterceptor databaseInitializionInterceptor, final ExecuteTimeInterceptor executeTimeInterceptor, final ParameterValidationInterceptor parameterValidationInterceptor, final ContentTypeInterceptor contentTypeInterceptor, final EntityManagerFactory entityManagerFactory) {
        this.databaseInitializionInterceptor = databaseInitializionInterceptor;
        this.executeTimeInterceptor = executeTimeInterceptor;
        this.parameterValidationInterceptor = parameterValidationInterceptor;
        this.contentTypeInterceptor = contentTypeInterceptor;

        this.osivInterceptor = new OSIVInterceptor();
        this.osivInterceptor.setEntityManagerFactory(entityManagerFactory);
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(databaseInitializionInterceptor);
        registry.addInterceptor(executeTimeInterceptor);
        registry.addInterceptor(parameterValidationInterceptor);
        // SIRI serves application/xml and swagger-ui serves html/css/js; this interceptor otherwise forces application/json onto every response.
        registry.addInterceptor(contentTypeInterceptor).excludePathPatterns(CONTEXT_PATH + "siri/**", "/swagger-ui/**");

        registry.addWebRequestInterceptor(osivInterceptor);
    }
}