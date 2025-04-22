package fi.livi.rata.avoindata.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.util.*;
import java.util.function.Function;

public class CsvUtil {
    private static final Logger log = LoggerFactory.getLogger(CsvUtil.class);

public static <T> List<T> readFile(final String fileName, final Function<String[], T> converter) {
        try {
            final ClassPathResource resource = new ClassPathResource(fileName);
            final List<T> result = new ArrayList<T>();

            try(final Scanner rowScanner = new Scanner(resource.getInputStream())) {
                while(rowScanner.hasNext()) {
                    result.add(converter.apply(rowScanner.nextLine().split(",")));
                }

                return result;
            }
        } catch (final Exception e) {
            log.error("Can't read from file {}", fileName, e);

            return Collections.emptyList();
        }
    }
}
