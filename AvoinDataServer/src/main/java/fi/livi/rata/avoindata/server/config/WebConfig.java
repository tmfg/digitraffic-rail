package fi.livi.rata.avoindata.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    public static final String CONTEXT_PATH = "/api/v1/";

    @Autowired
    private DatabaseInitializationInterceptor databaseInitializionInterceptor;

    @Autowired
    private ExecuteTimeInterceptor executeTimeInterceptor;

    @Autowired
    private ParameterValidationInterceptor parameterValidationInterceptor;

    @Autowired
    private ContentTypeInterceptor contentTypeInterceptor;

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(databaseInitializionInterceptor);
        registry.addInterceptor(executeTimeInterceptor);
        registry.addInterceptor(parameterValidationInterceptor);
        registry.addInterceptor(contentTypeInterceptor);
    }

    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        registry.addRedirectViewController("/configuration/ui", "/swagger-resources/configuration/ui");
        registry.addRedirectViewController("/configuration/security", "/swagger-resources/configuration/security");
    }

    @Override
    public void configurePathMatch(final PathMatchConfigurer configurer) {
        final UrlPathHelper urlPathHelper = new UrlPathHelper();
        urlPathHelper.setUrlDecode(false);

        configurer.setUrlPathHelper(urlPathHelper);
    }
}