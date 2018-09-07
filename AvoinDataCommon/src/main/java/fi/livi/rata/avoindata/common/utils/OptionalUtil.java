package fi.livi.rata.avoindata.common.utils;

import java.util.Optional;

import fi.livi.rata.avoindata.common.domain.common.NamedEntity;

public class OptionalUtil {
    public static <T extends NamedEntity> String getName(Optional<T> optional) {
        return optional.isPresent() ? optional.get().getName() : "Unknown";
    }
}
