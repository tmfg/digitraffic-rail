package fi.livi.rata.avoindata.updater.config;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class ApiKeyReqInterceptor implements ClientHttpRequestInterceptor {
    private String apiKey;

    public ApiKeyReqInterceptor(final String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ClientHttpResponse intercept(final HttpRequest request,
                                        final byte[] body,
                                        final ClientHttpRequestExecution execution) throws IOException {
        final HttpHeaders headers = request.getHeaders();
        headers.add("API-KEY", this.apiKey);

        return execution.execute(request, body);
    }
}
