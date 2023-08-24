package fi.livi.rata.avoindata.common.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(final List<String> strings) {
        return strings != null && !strings.isEmpty() ? String.join(SEPARATOR, strings) : null;
    }

    @Override
    public List<String> convertToEntityAttribute(final String string) {
        return string != null ? new ArrayList<>(Arrays.asList(string.split(SEPARATOR))) : null;
    }
}
