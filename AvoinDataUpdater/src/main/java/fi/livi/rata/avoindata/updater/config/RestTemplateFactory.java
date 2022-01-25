package fi.livi.rata.avoindata.updater.config;


import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
class RestTemplateFactory {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private MappingJackson2HttpMessageConverter messageConverter;

    @Value("${updater.http.initTimeoutMillis:300000}")
    private int READ_TIMEOUT;

    @Value("${updater.http.connectionTimoutMillis:30000}")
    private int CONNECTION_TIMEOUT;

    @Value("${javax.net.ssl.trustStore:}")
    private String trustStore;

    @Value("${javax.net.ssl.trustStorePassword:}")
    private String trustStorePassword;

    @Bean
    public RequestConfig requestConfig() {
        RequestConfig result = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT)
                .setSocketTimeout(READ_TIMEOUT)
                .build();
        return result;
    }

    @Bean
    public CloseableHttpClient httpClient(RequestConfig requestConfig) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        log.info("Creating trustStore");

        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;

        SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();

        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);

        CloseableHttpClient result = HttpClientBuilder
                .create()
                .setSSLSocketFactory(csf)
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
