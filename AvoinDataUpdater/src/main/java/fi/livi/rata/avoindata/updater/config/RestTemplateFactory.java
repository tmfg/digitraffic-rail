package fi.livi.rata.avoindata.updater.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;

@Configuration
class RestTemplateFactory {

    @Autowired
    private MappingJackson2HttpMessageConverter messageConverter;

    @Bean
    public AsyncRestTemplate asyncRestTemplate() {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setTaskExecutor(new SimpleAsyncTaskExecutor());
        requestFactory.setConnectTimeout(0);
        requestFactory.setReadTimeout(0);

        final AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();
        asyncRestTemplate.setMessageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter[]{messageConverter}));
        asyncRestTemplate.setAsyncRequestFactory(requestFactory);

        return asyncRestTemplate;
    }


    @Bean
    public RestTemplate createRestTemplate() {
        final RestTemplate restTemplate = new RestTemplate();
        restTemplate.setMessageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter[]{messageConverter}));
        return restTemplate;
    }
}
