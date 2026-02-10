package fi.livi.rata.avoindata.server.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
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

    private final DatabaseInitializationInterceptor databaseInitializionInterceptor;
    private final ExecuteTimeInterceptor executeTimeInterceptor;
    private final ParameterValidationInterceptor parameterValidationInterceptor;
    private final ContentTypeInterceptor contentTypeInterceptor;
    private final OSIVInterceptor osivInterceptor;

    public WebConfig(final DatabaseInitializationInterceptor databaseInitializionInterceptor, final ExecuteTimeInterceptor executeTimeInterceptor, final ParameterValidationInterceptor parameterValidationInterceptor, final ContentTypeInterceptor contentTypeInterceptor, final SessionFactory sessionFactory) {
        this.databaseInitializionInterceptor = databaseInitializionInterceptor;
        this.executeTimeInterceptor = executeTimeInterceptor;
        this.parameterValidationInterceptor = parameterValidationInterceptor;
        this.contentTypeInterceptor = contentTypeInterceptor;

        this.osivInterceptor = new OSIVInterceptor();
        this.osivInterceptor.setSessionFactory(sessionFactory);
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(databaseInitializionInterceptor);
        registry.addInterceptor(executeTimeInterceptor);
        registry.addInterceptor(parameterValidationInterceptor);
        registry.addInterceptor(contentTypeInterceptor);

        registry.addWebRequestInterceptor(osivInterceptor);
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