package fi.livi.rata.avoindata.server.integration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.assertj.core.util.Strings;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;

@Disabled
public class PerformanceTest {
    private static final int NUMBER_OF_THREADS = 15;
//    private static final String URL_FILE_PATH = "C:\\Users\\jaakkopa\\Documents\\url-muutokset.txt";
private static final String URL_FILE_PATH = "C:\\Users\\jaakkopa\\Documents\\urlit.txt";
    private static String BASE_URL = "http://localhost:5000";
//    private static String BASE_URL = "https://rata-beta.digitraffic.fi/";

    private Logger logger = LoggerFactory.getLogger(getClass());
    private AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
    private static Set<Integer> ALLOWED_CODES = Sets.newHashSet(200, 301);

    @Test
    public void performanceTest() throws URISyntaxException, IOException, ExecutionException, InterruptedException {
        final AsyncHttpClientConfig.Builder configBuilder = new AsyncHttpClientConfig.Builder();
        configBuilder.setCompressionEnforced(true);
        asyncHttpClient = new AsyncHttpClient(configBuilder.build());

        final Path path = Paths.get(URL_FILE_PATH);
        final List<String> urls = Lists.newArrayList(Iterables.filter(Files.readAllLines(path), s -> !Strings.isNullOrEmpty(s)));

        asyncFetchUrls(urls);
    }

    @Test
    public void schedulesShouldBeLimited() throws ExecutionException, InterruptedException {
        final List<String> urls = new ArrayList<>();
        final LocalDate startDate = LocalDate.of(2016, 6, 1);
        final LocalDate endDate = LocalDate.of(2016, 7, 1);
        for (LocalDate date = startDate; date.isBefore(endDate); date = date.plusDays(1)) {
            urls.add(String.format("api/v1/schedules?departure_date=%s", date));
        }

        asyncFetchUrls(urls);
    }

    private void asyncFetchUrls(final List<String> urls) throws InterruptedException, ExecutionException {
        final ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        final List<Future<Integer>> resultList = new ArrayList<>();

        final ZonedDateTime start = ZonedDateTime.now();

        for (final String url : urls) {
            final Future<Integer> result = executor.submit(() -> {
                try {
                    return assertUrl(getUrl(url));
                } catch (final InterruptedException | ExecutionException e) {
                    logger.error("error", e);
                }
                return 0;
            });
            resultList.add(result);
        }

        for (int i = 0; i < resultList.size(); i++) {
            final Future<Integer> integerFuture = resultList.get(i);
            final Integer integer = integerFuture.get();

            if (i % 100 == 0) {
                final long seconds = Duration.between(start, ZonedDateTime.now()).getSeconds();
                if (seconds != 0) {
                    logger.info("Total requests: {}, Seconds run: {}, Requests per second: {}", i, seconds, (double) i / (double) seconds);
                }
            }
        }

        final ZonedDateTime end = ZonedDateTime.now();

        logger.info("Start: {}, End {}, Duration: {}, Urls: {}", start, end, Duration.between(start, end), urls.size());
    }

    private String getUrl(final String path) {
        return BASE_URL + path;
    }

    private int assertUrl(final String url) throws InterruptedException, ExecutionException {
        final Future<Response> f = asyncHttpClient.prepareGet(url).execute();
        final Response response = f.get();
        final int statusCode = response.getStatusCode();

        final boolean isStatuscodeAllowed = ALLOWED_CODES.contains(statusCode);
        if (!isStatuscodeAllowed) {
            logger.error("Statuscode: {}. Allowed statuscodes: {}. Url: {}", statusCode, ALLOWED_CODES, url);
        } else {
            logger.trace("Fetch succesful. Statuscode: {}. Url: {}", statusCode, url);
        }

        return statusCode;
    }
}
