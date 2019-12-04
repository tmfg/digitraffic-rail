package fi.livi.rata.avoindata.updater.config;


import java.util.Arrays;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
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
    public RequestConfig requestConfig() {
        RequestConfig result = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(READ_TIMEOUT)
                .build();
        return result;
    }

    @Bean
    public CloseableHttpClient httpClient(RequestConfig requestConfig) {
        CloseableHttpClient result = HttpClientBuilder
                .create()
                .setDefaultRequestConfig(requestConfig)
                .build();
        return result;
    }

    @Bean
    public RestTemplate restTemplate(HttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setMessageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter[]{messageConverter}));
        return restTemplate;
    }
}
