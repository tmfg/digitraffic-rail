package fi.livi.rata.avoindata.common.converter;

import fi.livi.rata.avoindata.common.domain.trackwork.LocationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RumaLocationTypeConverter implements AttributeConverter<LocationType, String> {

    @Override
    public String convertToDatabaseColumn(final LocationType attribute) {
        if (attribute == null) {
            return null;
        }
        return String.valueOf(attribute.ordinal());
    }

    @Override
    public LocationType convertToEntityAttribute(final String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        final int ordinal = Integer.parseInt(dbData);
        return LocationType.values()[ordinal];
    }
}
