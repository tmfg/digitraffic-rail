package fi.livi.rata.avoindata.updater.config;


import com.amazonaws.xray.proxies.apache.http.HttpClientBuilder;
import org.apache.http.client.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.util.Arrays;

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

        HttpClient httpClient = HttpClientBuilder.create().build();
        final ClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

        restTemplateBuilder.messageConverters(Arrays.asList(messageConverter));
        restTemplateBuilder.setConnectTimeout(CONNECTION_TIMEOUT);
        restTemplateBuilder.setReadTimeout(READ_TIMEOUT);
        restTemplateBuilder.requestFactory(() -> requestFactory);

        return restTemplateBuilder.build();
    }
}
