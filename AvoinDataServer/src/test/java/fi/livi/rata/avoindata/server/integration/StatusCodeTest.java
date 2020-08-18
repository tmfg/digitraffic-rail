package fi.livi.rata.avoindata.server.integration;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;

@Ignore
public class StatusCodeTest {
    private Logger logger = LoggerFactory.getLogger(StatusCodeTest.class);
    private AsyncHttpClient asyncHttpClient = new AsyncHttpClient();
    private static String BASE_URL = "https://rata-beta.digitraffic.fi/";
//    private static String BASE_URL = "http://localhost:5000/";
//    private static String BASE_URL = "http://front-prd.integraatiot.eu/";
//    private static String BASE_URL = "http://finnishtransportagency.github.io/digitraffic/rautatieliikenne/";

    private static String DOCUMENTATION_URL = "http://rata.digitraffic.fi";
    private static String INFRA_DOCUMENTATION_URL = BASE_URL + "infra-api/";
    private static String JETI_DOCUMENTATION_URL = BASE_URL + "jeti-api/";
    private static Set<Integer> ALLOWED_CODES = Sets.newHashSet(200, 301, 303, 307);
    private static String CURRENT_DATE = LocalDate.now().toString();

    @Test
    @Ignore
    public void schedulesShouldBeLimited() throws IOException, ExecutionException, InterruptedException {
        Set<String> urls = new HashSet<>();


        LocalDate start = LocalDate.of(2017, 1, 12);
        LocalDate end = LocalDate.of(2017, 1, 26);

        for (LocalDate date = start; date.isBefore(end); date = date.plusDays(1)) {
            urls.add(getUrl(String.format("api/v1/schedules?departure_date=%s", date)));
        }

        final Map<String, Future<Response>> responses = getResponseFutures(urls);

        List<Integer> statusCodes = new ArrayList<>();
        for (String url : responses.keySet()) {
            final Future<Response> response = responses.get(url);
            final int statusCode = response.get().getStatusCode();
            statusCodes.add(statusCode);
            logger.info("{} returned status code {}", url, statusCode);
        }

        Assert.assertTrue("429 should be present", statusCodes.contains(429));
    }

    @Test
    public void timestampsMatchOnPrdOneAndTwo() throws ExecutionException, InterruptedException, IOException {
        final String prd1Url = "http://alb.prd.rata.digitraffic.fi/api/v1/schedules/1?date=2017-05-19";
//        final String prd1Url = BASE_URL + "api/v1/schedules/1?date=2017-05-19";

        final String prd2Url = "http://alb.prd2.rata.digitraffic.fi/api/v1/schedules/1?date=2017-05-19";
//        final String prd2Url = BASE_URL + "api/v1/schedules/1?date=2017-05-19";

        final Map<String, Future<Response>> responseFutures = getResponseFutures(Sets.newHashSet(prd1Url, prd2Url));

        final Future<Response> prd1Future = responseFutures.get(prd1Url);
        final Future<Response> prd2Future = responseFutures.get(prd2Url);

        Assert.assertTrue(prd1Future.get().getResponseBody().contains("2017-05-19T04:23:00.000Z"));
        Assert.assertTrue(prd2Future.get().getResponseBody().contains("2017-05-19T04:23:00.000Z"));
    }

    @Test
    @Ignore
    public void cacheHeadersAreEqual() throws IOException, ExecutionException, InterruptedException {
        Set<String> ignoredUrls = Sets.newHashSet("http://uptime.statuscake.com/?TestID=KIvdE8ZaAe", "https://rata.digitraffic.fi/");

        final String localhost = "http://localhost:5000/";
        final String production = "http://rata.digitraffic.fi/";
        Set<String> localUrls = getUrlsFromDocumentation(localhost + "api/v1/doc/index.html", ignoredUrls);
        Set<String> prdUrls = getUrlsFromDocumentation(production + "api/v1/doc/index.html", ignoredUrls);


        final Map<String, Response> localResponses = getResponses(localUrls);
        final Map<String, Response> prdResponses = getResponses(prdUrls);

        for (final String url : prdResponses.keySet()) {
            final String shortPart = url.replace(production, "");
            final Response localResponse = localResponses.get(localhost + shortPart);
            final Response prdResponse = prdResponses.get(production + shortPart);

            final String localCacheControl = localResponse.getHeader("cache-control");
            final String prdCacheControl = prdResponse.getHeader("cache-control");

            if (localCacheControl == null && prdCacheControl == null) {
                continue;
            } else if (localCacheControl == null && prdCacheControl != null) {
                logger.warn("Url: {}, Local: {}, Prd: {}", shortPart, localCacheControl, prdCacheControl);
            } else if (!localCacheControl.equals(prdCacheControl)) {
                logger.warn("Url: {}, Local: {}, Prd: {}", shortPart, localCacheControl, prdCacheControl);
            }
        }
    }

    @Test
    public void urlsWorkInDocumentation() throws IOException, ExecutionException, InterruptedException {
        Set<String> urls = getUrlsFromDocumentation(DOCUMENTATION_URL, Sets.newHashSet("https://www.digitraffic.fi"));

        final Iterable<String> digitrafficUrls = Iterables.filter(urls, url ->
                url.contains("digitraffic.fi") &&
                        !url.contains("graphql") &&
                        !url.contains("list.html") &&
                        !url.contains("https://rata.digitraffic.fi/jeti-api/") &&
                        !url.contains("https://rata.digitraffic.fi/infra-api/") &&
                        !url.contains("https://rata.digitraffic.fi/history/"));

        List<String> targetUrls = new ArrayList<>();
        for (String digitrafficUrl : digitrafficUrls) {
            targetUrls.add(digitrafficUrl.replace("https://rata.digitraffic.fi/", BASE_URL));
        }

        assertUrls(targetUrls);
    }

    @Test
    public void urlsWorkInJetiDocumentation() throws IOException, ExecutionException, InterruptedException {
        Set<String> ignoredUrls = Sets.newHashSet("http://uptime.statuscake.com/?TestID=KIvdE8ZaAe", "https://rata.digitraffic.fi/",
                "http://fi.wikipedia.org/wiki/Sarjatunnus", "http://creativecommons.org/licenses/by/4.0/", "http://www.liikennevirasto.fi");

        Set<String> urls = getUrlsFromDocumentation(JETI_DOCUMENTATION_URL, ignoredUrls);

        assertUrls(urls);
    }

    @Test
    public void urlsWorkInInfraDocumentation() throws IOException, ExecutionException, InterruptedException {
        Set<String> ignoredUrls = Sets.newHashSet("http://uptime.statuscake.com/?TestID=KIvdE8ZaAe", "https://rata.digitraffic.fi/",
                "http://fi.wikipedia.org/wiki/Sarjatunnus", "http://creativecommons.org/licenses/by/4.0/", "http://www.liikennevirasto.fi");

        Set<String> urls = getUrlsFromDocumentation(INFRA_DOCUMENTATION_URL, ignoredUrls);

        assertUrls(urls);
    }

    @Test
    public void webSocketUrlsWork() throws ExecutionException, InterruptedException {
        Set<String> urls = new HashSet<>();

        urls.add(getUrl(String.format("api/v1/websockets/info?t=1456393715029", CURRENT_DATE)));
        urls.add(getUrl(String.format("api/v1/websockets/train-tracking/info?t=1456393715029", CURRENT_DATE)));

        assertUrls(urls);
    }

    @Test
    public void urlRewritingWorks() throws ExecutionException, InterruptedException, IOException {
        Set<String> urls = new HashSet<>();
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
        Set<String> urls = new HashSet<>();
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

    private String getUrl(String path) {
        return BASE_URL + path;
    }

    private Set<String> getUrlsFromDocumentation(final String documentationUrl, final Set<String> ignoredUrls) throws IOException {
        Document doc = Jsoup.connect(documentationUrl).get();
        final Elements links = doc.getElementsByTag("a");
        Set<String> urls = new HashSet<>();
        for (final Element link : links) {
            final String href = link.absUrl("href");
            if (!href.contains("#") && !ignoredUrls.contains(href)) {
                urls.add(href);
            }
        }
        return urls;
    }

    private Map<String, Response> getResponses(final Iterable<String> urls) throws ExecutionException, InterruptedException {
        Map<String, Response> responses = new HashMap<>();
        for (final String url : urls) {
            logger.info("Testing: {}", url);
            Future<Response> f = asyncHttpClient.prepareGet(url).addHeader("accept-encoding", "gzip").execute();
            final Response response = f.get();
            responses.put(url, response);
        }

        return responses;
    }

    private Map<String, Future<Response>> getResponseFutures(final Set<String> urls) {
        Map<String, Future<Response>> responses = new HashMap<>(urls.size());
        for (final String url : urls) {
            logger.info("Testing: {}", url);
            Future<Response> f = asyncHttpClient.prepareGet(url).execute();
            responses.put(url, f);
        }

        return responses;
    }

    private void assertUrls(final Iterable<String> urls) throws InterruptedException, ExecutionException {
        final Map<String, Response> responses = getResponses(urls);

        for (final String url : responses.keySet()) {
            final Response response = responses.get(url);
            final int statusCode = response.getStatusCode();
            logger.info("Url: {}, StatusCode: {}", url, statusCode);
            Assert.assertTrue(ALLOWED_CODES.contains(statusCode));
        }
    }
}
