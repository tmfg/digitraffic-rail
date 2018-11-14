package fi.livi.rata.avoindata.updater.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
class RestTemplateFactory {

    @Autowired
    private MappingJackson2HttpMessageConverter messageConverter;

    @Value("${updater.http.initTimeoutMillis:300000}")
    private int READ_TIMEOUT;

    @Value("${updater.http.connectionTimoutMillis:30000}")
    private int CONNECTION_TIMEOUT;

    @Bean
    public RestTemplate createRestTemplate(RestTemplateBuilder restTemplateBuilder) {
        restTemplateBuilder.messageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter[]{messageConverter}));
        restTemplateBuilder.setConnectTimeout(CONNECTION_TIMEOUT);
        restTemplateBuilder.setReadTimeout(READ_TIMEOUT);

        return restTemplateBuilder.build();
    }
}
