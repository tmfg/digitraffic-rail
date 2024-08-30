package fi.livi.rata.avoindata.updater.config;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.ssl.TrustStrategy;
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

    @Bean
    public RequestConfig requestConfig(
            @Value("${updater.http.connectionTimoutMillis:30000}")
            final int CONNECTION_TIMEOUT) {
        return RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(CONNECTION_TIMEOUT))
                .build();
    }

    public CloseableHttpClient createInsecureHttpClient(final RequestConfig requestConfig)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Creating insecure HTTP client");
        final TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        final SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(acceptingTrustStrategy)
                .build();

        final SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        final HttpClientConnectionManager hccm = PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(csf).build();
        return HttpClientBuilder
                .create()
                .setConnectionManager(hccm)
                .setConnectionReuseStrategy((httpRequest, httpResponse, httpContext) -> false)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public CloseableHttpClient createSecureHttpClient(final RequestConfig requestConfig) {
        log.info("Creating secure HTTP client");
        final HttpClientConnectionManager hccm = PoolingHttpClientConnectionManagerBuilder.create().build();
        return HttpClientBuilder
                .create()
                .setConnectionManager(hccm)
                .setConnectionReuseStrategy((httpRequest, httpResponse, httpContext) -> false)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public CloseableHttpClient httpClient(final RequestConfig requestConfig,
                                          @Value("${updater.validate-ripa-cert:true}")
                                          final boolean validateRipaCertificate)
            throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        /*
           SSL certificate validation needs to be disabled locally for the application to be able
           to access RIPA when connecting via SSM.
        */
        if (validateRipaCertificate) {
            return createSecureHttpClient(requestConfig);
        } else {
            return createInsecureHttpClient(requestConfig);
        }
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
    public RestTemplate ripaRestTemplate(final HttpClient httpClient,
                                         @Value("${updater.reason.api-key}")
                                         final String apiKey) {
        final RestTemplate template = this.restTemplate(httpClient);
        template.getInterceptors().add(new ApiKeyReqInterceptor(apiKey));

        return template;
    }
}