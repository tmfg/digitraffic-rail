package fi.livi.rata.avoindata.server.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled
public class StatusCodeTest {
    private final Logger logger = LoggerFactory.getLogger(StatusCodeTest.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String BASE_URL = "https://rata-beta.digitraffic.fi/";
//    private static String BASE_URL = "http://localhost:5000/";
//    private static String BASE_URL = "http://front-prd.integraatiot.eu/";
//    private static String BASE_URL = "http://finnishtransportagency.github.io/digitraffic/rautatieliikenne/";

    private static final String DOCUMENTATION_URL = "http://rata.digitraffic.fi";
    private static final String INFRA_DOCUMENTATION_URL = BASE_URL + "infra-api/";
    private static final String JETI_DOCUMENTATION_URL = BASE_URL + "jeti-api/";
    private static final Set<Integer> ALLOWED_CODES = Sets.newHashSet(200, 301, 303, 307);
    private static final String CURRENT_DATE = LocalDate.now().toString();

    @Test
    @Disabled
    public void schedulesShouldBeLimited() throws ExecutionException, InterruptedException {
        final Set<String> urls = new HashSet<>();


        final LocalDate start = LocalDate.of(2017, 1, 12);
        final LocalDate end = LocalDate.of(2017, 1, 26);

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            urls.add(getUrl(String.format("api/v1/schedules?departure_date=%s", date)));
        }

        final Map<String, CompletableFuture<HttpResponse<String>>> responses = getResponseFutures(urls);

        final List<Integer> statusCodes = new ArrayList<>();
        for (final String url : responses.keySet()) {
            final CompletableFuture<HttpResponse<String>> response = responses.get(url);
            final int statusCode = response.get().statusCode();
            statusCodes.add(statusCode);
            logger.info("{} returned status code {}", url, statusCode);
        }
        assertTrue(statusCodes.contains(429), "429 should be present");
    }

    @Test
    public void timestampsMatchOnPrdOneAndTwo() throws ExecutionException, InterruptedException {
        final String prd1Url = "http://alb.prd.rata.digitraffic.fi/api/v1/schedules/1?date=2017-05-19";
//        final String prd1Url = BASE_URL + "api/v1/schedules/1?date=2017-05-19";

        final String prd2Url = "http://alb.prd2.rata.digitraffic.fi/api/v1/schedules/1?date=2017-05-19";
//        final String prd2Url = BASE_URL + "api/v1/schedules/1?date=2017-05-19";

        final Map<String, CompletableFuture<HttpResponse<String>>> responseFutures = getResponseFutures(Sets.newHashSet(prd1Url, prd2Url));

        final CompletableFuture<HttpResponse<String>> prd1Future = responseFutures.get(prd1Url);
        final CompletableFuture<HttpResponse<String>> prd2Future = responseFutures.get(prd2Url);

        assertTrue(prd1Future.get().body().contains("2017-05-19T04:23:00.000Z"));
        assertTrue(prd2Future.get().body().contains("2017-05-19T04:23:00.000Z"));
    }

    @Test
    @Disabled
    public void cacheHeadersAreEqual() throws IOException, ExecutionException, InterruptedException {
        final Set<String> ignoredUrls = Sets.newHashSet("http://uptime.statuscake.com/?TestID=KIvdE8ZaAe", "https://rata.digitraffic.fi/", "https://www.youtube.com/channel/UCpnhwBRjt58yUu_Oky7vyxQ");

        final String localhost = "http://localhost:5000/";
        final String production = "http://rata.digitraffic.fi/";
        final Set<String> localUrls = getUrlsFromDocumentation(localhost + "api/v1/doc/index.html", ignoredUrls);
        final Set<String> prdUrls = getUrlsFromDocumentation(production + "api/v1/doc/index.html", ignoredUrls);


        final Map<String, HttpResponse<String>> localResponses = getResponses(localUrls);
        final Map<String, HttpResponse<String>> prdResponses = getResponses(prdUrls);

        for (final String url : prdResponses.keySet()) {
            final String shortPart = url.replace(production, "");
            final HttpResponse<String> localResponse = localResponses.get(localhost + shortPart);
            final HttpResponse<String> prdResponse = prdResponses.get(production + shortPart);

            final Optional<String> localCacheControl = localResponse.headers().firstValue("cache-control");
            final Optional<String>  prdCacheControl = prdResponse.headers().firstValue("cache-control");

            if (localCacheControl.isPresent() || prdCacheControl.isPresent()) {
                if (!localCacheControl.equals(prdCacheControl)) {
                    logger.warn("Url: {}, Local: {}, Prd: {}", shortPart, localCacheControl, prdCacheControl);
                } if (!localCacheControl.equals(prdCacheControl)) {
                    logger.warn("Url: {}, Local: {}, Prd: {}", shortPart, localCacheControl, prdCacheControl);
                }
            }
        }
    }

    @Test
    public void urlsWorkInDocumentation() throws IOException, ExecutionException, InterruptedException {
        final Set<String> urls = getUrlsFromDocumentation(DOCUMENTATION_URL, Sets.newHashSet("https://www.digitraffic.fi"));

        final Iterable<String> digitrafficUrls = Iterables.filter(urls, url ->
                url.contains("digitraffic.fi") &&
                        !url.contains("graphql") &&
                        !url.contains("list.html") &&
                        !url.contains("https://rata.digitraffic.fi/jeti-api/") &&
                        !url.contains("https://rata.digitraffic.fi/infra-api/") &&
                        !url.contains("https://rata.digitraffic.fi/history/"));

        final List<String> targetUrls = new ArrayList<>();
        for (final String digitrafficUrl : digitrafficUrls) {
            targetUrls.add(digitrafficUrl.replace("https://rata.digitraffic.fi/", BASE_URL));
        }

        assertUrls(targetUrls);
    }

    @Test
    public void urlsWorkInJetiDocumentation() throws IOException, ExecutionException, InterruptedException {
        final Set<String> ignoredUrls = Sets.newHashSet("http://uptime.statuscake.com/?TestID=KIvdE8ZaAe", "https://rata.digitraffic.fi/",
                "http://fi.wikipedia.org/wiki/Sarjatunnus", "http://creativecommons.org/licenses/by/4.0/", "http://www.liikennevirasto.fi");

        final Set<String> urls = getUrlsFromDocumentation(JETI_DOCUMENTATION_URL, ignoredUrls);

        assertUrls(urls);
    }

    @Test
    public void urlsWorkInInfraDocumentation() throws IOException, ExecutionException, InterruptedException {
        final Set<String> ignoredUrls = Sets.newHashSet("http://uptime.statuscake.com/?TestID=KIvdE8ZaAe", "https://rata.digitraffic.fi/",
                "http://fi.wikipedia.org/wiki/Sarjatunnus", "http://creativecommons.org/licenses/by/4.0/", "http://www.liikennevirasto.fi");

        final Set<String> urls = getUrlsFromDocumentation(INFRA_DOCUMENTATION_URL, ignoredUrls);

        assertUrls(urls);
    }

    @Test
    public void webSocketUrlsWork() throws ExecutionException, InterruptedException {
        final Set<String> urls = new HashSet<>();

        urls.add(getUrl(String.format("api/v1/websockets/info?t=1456393715029", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/websockets/train-tracking/info?t=1456393715029", CURRENT_DATE)));

        assertUrls(urls);
    }

    @Test
    public void urlRewritingWorks() throws ExecutionException, InterruptedException {
        final Set<String> urls = new HashSet<>();
        urls.add(getUrl(String.format("api/v1/compositions/1?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/history/1?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/history?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/live-trains/1?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/schedules/1?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/schedules?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/train-tracking/1?departure_date=%s", CURRENT_DATE)));

        urls.add(getUrl(String.format("api/v1/compositions/1?date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/history/1?date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/history?date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/live-trains/1?date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/schedules/1?date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/schedules?date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/train-tracking/1?date=%s", CURRENT_DATE)));

        assertUrls(urls);
    }

    @Test
    public void urlsTrainRunMessageWorks() throws ExecutionException, InterruptedException {
        final Set<String> urls = new HashSet<>();
        urls.add(getUrl("api/v1/train-tracking/1"));
        urls.add(getUrl(String.format("api/v1/train-tracking/1?departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/train-tracking/1?departure_date=%s&version=0", CURRENT_DATE)));

        urls.add(getUrl(String.format("api/v1/train-tracking/?station=HKI&departure_date=%s", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/train-tracking/?station=HKI&track_section=014b&departure_date=%s", CURRENT_DATE)));

        urls.add(getUrl("api/v1/train-tracking/?station=HKI&track_section=014b"));
        urls.add(getUrl("api/v1/train-tracking/?station=HKI&track_section=014b&limit=500"));

        urls.add(getUrl("api/v1/train-tracking/?version=-1"));
        assertUrls(urls);
    }

    private String getUrl(final String path) {
        return BASE_URL + path;
    }

    private Set<String> getUrlsFromDocumentation(final String documentationUrl, final Set<String> ignoredUrls) throws IOException {
        final Document doc = Jsoup.connect(documentationUrl).get();
        final Elements links = doc.getElementsByTag("a");
        final Set<String> urls = new HashSet<>();
        for (final Element link : links) {
            final String href = link.absUrl("href");
            if (!href.contains("#") && !ignoredUrls.contains(href)) {
                urls.add(href);
            }
        }
        return urls;
    }

    public Map<String, HttpResponse<String>> getResponses(final Iterable<String> urls) throws ExecutionException, InterruptedException {

        final Map<String, HttpResponse<String>> responses = new HashMap<>();
        final List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        final List<String> urlList = new ArrayList<>();

        for (final String url : urls) {
            logger.info("Testing: {}", url);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();

            final CompletableFuture<HttpResponse<String>> future =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            futures.add(future);
            urlList.add(url);
        }

        for (int i = 0; i < futures.size(); i++) {

            try {
                final HttpResponse<String> response = futures.get(i).get(); // waits for completion
                responses.put(urlList.get(i), response);
            } catch (final InterruptedException | ExecutionException e) {
                logger.error("Error on url {}" , urlList.get(i));
                throw e;
            }
        }

        return responses;
    }


//    private Map<String, Future<Response>> getResponseFutures(final Set<String> urls) {
//        final Map<String, Future<Response>> responses = new HashMap<>(urls.size());
//        for (final String url : urls) {
//            logger.info("Testing: {}", url);
//            final Future<Response> f = asyncHttpClient.prepareGet(url).execute();
//            responses.put(url, f);
//        }
//
//        return responses;
//    }
    public Map<String, CompletableFuture<HttpResponse<String>>> getResponseFutures(final Set<String> urls) {
        final Map<String, CompletableFuture<HttpResponse<String>>> responses = new HashMap<>(urls.size());

        for (final String url : urls) {
            logger.info("Testing: {}", url);
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();

            // sendAsync returns a CompletableFuture<HttpResponse<T>>
            final CompletableFuture<HttpResponse<String>> future =
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

            responses.put(url, future);
        }

        return responses;
    }

    private void assertUrls(final Iterable<String> urls) throws InterruptedException, ExecutionException {
        //final Map<String, Response> responses = getResponses(urls);
        final Map<String, HttpResponse<String>> responses = getResponses(urls);

        for (final String url : responses.keySet()) {
            //final Response response = responses.get(url);
            final HttpResponse<String> response = responses.get(url);
            final int statusCode = response.statusCode();
            logger.info("Url: {}, StatusCode: {}", url, statusCode);
            assertTrue(ALLOWED_CODES.contains(statusCode));
        }
    }
}
