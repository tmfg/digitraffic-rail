package fi.livi.rata.avoindata.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
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
    public void serialize(final BigDecimal value, final JsonGenerator gen, final SerializerProvider arg2)
            throws IOException {
        gen.writeNumber(scale(value));
    }

    public static BigDecimal scale(final BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_DOWN);
    }
}