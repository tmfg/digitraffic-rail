package fi.livi.rata.avoindata.updater.service.netex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Writes a sample dataset to target/netex-sample so it can be eyeballed and fed
 * to the Entur validator. Not a test; invoked manually.
 */
final class NeTExSampleDump {

    private NeTExSampleDump() {
    }

    static void dump(final byte[] zip, final String directory) throws IOException {
        final Path out = Path.of(directory);
        Files.createDirectories(out);
        try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Files.write(out.resolve(entry.getName()), zis.readAllBytes());
            }
        }
    }
}
