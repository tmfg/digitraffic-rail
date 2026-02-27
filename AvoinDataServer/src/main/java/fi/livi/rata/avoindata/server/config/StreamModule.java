package fi.livi.rata.avoindata.server.config;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;
import tools.jackson.databind.type.*;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * Jackson 3 module for serializing Java Streams as JSON arrays.
 */
public class StreamModule extends SimpleModule {

    private static final long serialVersionUID = -1324033833221219001L;

    @Override
    public void setupModule(final SetupContext context) {
        context.addTypeModifier(new StreamTypeModifier());
        context.addSerializers(new StreamSerializers());
    }

    /**
     * Type modifier that makes Stream types appear as collection-like types.
     */
    public static final class StreamTypeModifier extends TypeModifier {

        @Override
        public JavaType modifyType(final JavaType type, final Type jdkType, final TypeBindings context, final TypeFactory typeFactory) {
            if (type.isReferenceType() || type.isContainerType()) {
                return type;
            }

            final Class<?> raw = type.getRawClass();

            if (Stream.class.isAssignableFrom(raw)) {
                final JavaType[] params = typeFactory.findTypeParameters(type, Stream.class);

                if (params == null || params.length == 0) {
                    return ReferenceType.upgradeFrom(type, type.containedTypeOrUnknown(0));
                }

                return typeFactory.constructCollectionLikeType(raw, params[0]);
            }
            return type;
        }
    }

    /**
     * Serializer provider for Stream types.
     */
    public static final class StreamSerializers extends tools.jackson.databind.ser.Serializers.Base {

        @Override
        public ValueSerializer<?> findCollectionLikeSerializer(final SerializationConfig config,
                final CollectionLikeType type, final BeanDescription.Supplier beanDescSupplier,
                final JsonFormat.Value formatOverrides,
                final tools.jackson.databind.jsontype.TypeSerializer elementTypeSerializer,
                final ValueSerializer<Object> elementValueSerializer) {

            final Class<?> raw = type.getRawClass();

            if (Stream.class.isAssignableFrom(raw)) {
                return new StreamSerializer();
            }

            return null;
        }
    }

    /**
     * Simple serializer that writes a Stream as a JSON array.
     */
    @SuppressWarnings("rawtypes")
    public static final class StreamSerializer extends StdSerializer<Stream> {

        private static final long serialVersionUID = -455534622397905995L;

        public StreamSerializer() {
            super(Stream.class);
        }

        @Override
        public void serialize(final Stream value, final JsonGenerator gen, final SerializationContext provider) {
            gen.writeStartArray();
            try (final Stream<?> stream = value) {
                final Iterator<?> iterator = stream.iterator();
                while (iterator.hasNext()) {
                    gen.writePOJO(iterator.next());
                }
            }
            gen.writeEndArray();
        }
    }
}

