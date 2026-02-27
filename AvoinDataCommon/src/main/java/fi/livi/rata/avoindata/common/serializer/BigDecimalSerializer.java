package fi.livi.rata.avoindata.common.serializer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalSerializer extends StdSerializer<BigDecimal> {
    public BigDecimalSerializer() {
        this(null);
    }

    public BigDecimalSerializer(final Class<BigDecimal> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(final BigDecimal value, final JsonGenerator gen, final SerializationContext arg2) {
        gen.writeNumber(scale(value));
    }

    public static BigDecimal scale(final BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_DOWN);
    }
}