package fi.livi.rata.avoindata.updater.config;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
class RestTemplateFactory {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MappingJackson2HttpMessageConverter messageConverter;

    @Value("${updater.http.connectionTimoutMillis:30000}")
    private int CONNECTION_TIMEOUT;

    @Value("${updater.reason.api-key}")
    private String apiKey;

    @Bean
    public RequestConfig requestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECTION_TIMEOUT))
                .build();
    }

    @Bean
    public CloseableHttpClient httpClient(final RequestConfig requestConfig)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        final HttpClientConnectionManager hccm = PoolingHttpClientConnectionManagerBuilder.create().build();
        return HttpClientBuilder
                .create()
                .setConnectionManager(hccm)
                .setConnectionReuseStrategy((httpRequest, httpResponse, httpContext) -> false)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean(name = "normalRestTemplate")
    public RestTemplate restTemplate(final HttpClient httpClient) {
        final HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);

        final RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.setInterceptors(List.of(new UserHeaderReqInterceptor(),
                (request, body, execution) -> {
                    final HttpHeaders headers = request.getHeaders();
                    headers.add(HttpHeaders.CONNECTION, "close");

                    return execution.execute(request, body);
                }));
        restTemplate.setMessageConverters(Arrays.asList(new MappingJackson2HttpMessageConverter[] { messageConverter }));

        return restTemplate;
    }

    @Bean(name = "ripaRestTemplate")
    public RestTemplate ripaRestTemplate(final HttpClient httpClient) {
        final RestTemplate template = this.restTemplate(httpClient);
        template.getInterceptors().add(new ApiKeyReqInterceptor(this.apiKey));

        return template;
    }
}