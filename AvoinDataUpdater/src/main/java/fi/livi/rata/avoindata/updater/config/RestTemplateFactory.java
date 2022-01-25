package fi.livi.rata.avoindata.updater.config;


import java.io.IOException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.google.common.base.Strings;

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
    public CloseableHttpClient httpClient(RequestConfig requestConfig) throws IOException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        SSLContext sslContext = null;
        if (!Strings.isNullOrEmpty(trustStore)) {
            String url = "file://" + trustStore;
            log.info("Creating trustStore: {}", url);
            sslContext = SSLContextBuilder.create().loadTrustMaterial(new URL(url), trustStorePassword.toCharArray()).build();
        }

        CloseableHttpClient result = HttpClientBuilder
                .create()
                .setSSLContext(sslContext)
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
