package fi.livi.rata.avoindata.server.controller.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;

public class CacheControlExportHeadersTest {

    private static GeneratedExport export(final int minutesAgo) {
        final GeneratedExport export = new GeneratedExport();
        export.data = "<Siri/>".getBytes(StandardCharsets.UTF_8);
        export.created = DateProvider.nowInHelsinki().minusMinutes(minutesAgo);
        return export;
    }

    @Test
    public void freshExportHeaders() {
        final GeneratedExport export = export(1);

        final HttpHeaders headers = CacheControl.exportHeaders(export, 30, 5);

        assertEquals("max-age=30, public", headers.getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("true", headers.getFirst("x-is-fresh"));
        assertEquals(export.created.toString(), headers.getFirst("x-timestamp"));
        assertEquals("7", headers.getFirst(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    public void exportOlderThanLimitIsNotFresh() {
        assertEquals("false", CacheControl.exportHeaders(export(10), 30, 5).getFirst("x-is-fresh"));
    }
}
