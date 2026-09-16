package fi.livi.rata.avoindata.updater.service.gtfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;

/**
 * Pruning must never remove the newest export of a file name, or a feed whose generation has been failing
 * ends up with nothing to serve.
 */
class GTFSWritingServiceDeleteOldZipsTest {

    private GeneratedExportRepository gtfsRepository;
    private GTFSWritingService service;

    @BeforeEach
    void setUp() throws Exception {
        gtfsRepository = mock(GeneratedExportRepository.class);
        service = new GTFSWritingService();
        final Field field = GTFSWritingService.class.getDeclaredField("gtfsRepository");
        field.setAccessible(true);
        field.set(service, gtfsRepository);
    }

    @Test
    void givenExportsExist_whenDeletingOldZips_thenNewestPerFileNameIsKept() {
        when(gtfsRepository.findNewestIdPerFileName()).thenReturn(List.of(10L, 20L, 30L));
        when(gtfsRepository.deleteOldZips(any(), anyCollection())).thenReturn(5);

        service.deleteOldZips();

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<Long>> keep = ArgumentCaptor.forClass(Collection.class);
        verify(gtfsRepository).deleteOldZips(any(ZonedDateTime.class), keep.capture());
        assertEquals(List.of(10L, 20L, 30L), List.copyOf(keep.getValue()));
    }

    @Test
    void givenEmptyTable_whenDeletingOldZips_thenNoDeleteIsIssued() {
        // "not in ()" is not valid SQL, and there is nothing to prune anyway
        when(gtfsRepository.findNewestIdPerFileName()).thenReturn(List.of());

        service.deleteOldZips();

        verify(gtfsRepository, never()).deleteOldZips(any(), anyCollection());
    }
}
