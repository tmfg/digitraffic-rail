package fi.livi.rata.avoindata.server.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BasicSerializerFactory;
import com.fasterxml.jackson.databind.ser.BeanSerializerFactory;
import com.fasterxml.jackson.databind.ser.std.AsArraySerializerBase;
import com.fasterxml.jackson.databind.type.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.stream.Stream;

/**
 * The Class StreamModule.
 *
 * @author jmaxwell
 */
public class StreamModule extends SimpleModule {

    /** The Constant serialVersionUID. */
    private static final long serialVersionUID = -1324033833221219001L;

    @Override
    public void setupModule(final SetupContext context) {
        context.addTypeModifier(new StreamTypeModifier());
        context.addSerializers(new StreamSerializers());
    }

    /**
     * The Class StreamTypeModifier.
     */
    public static final class StreamTypeModifier extends TypeModifier {





         @Override
         public JavaType modifyType(JavaType type, Type jdkType, TypeBindings context, TypeFactory typeFactory) {

         if (type.isReferenceType() || type.isContainerType()) {
         return type;
         }

         Class<?> raw = type.getRawClass();

         if (Stream.class.isAssignableFrom(raw)) {

         JavaType[] params = typeFactory.findTypeParameters(type, Stream.class);

         if (params == null || params.length == 0) {

         return ReferenceType.upgradeFrom(type, type.containedTypeOrUnknown(0));
         }

         return typeFactory.constructCollectionLikeType(raw, params[0]);

         }
         return type;
         }


    }

    /**
     * The Class StreamSerializers.
     */
    public static final class StreamSerializers extends com.fasterxml.jackson.databind.ser.Serializers.Base {

        @Override
        public JsonSerializer<?> findCollectionLikeSerializer(final SerializationConfig config,
                final CollectionLikeType type, final BeanDescription beanDesc,
                final TypeSerializer elementTypeSerializer, final JsonSerializer<Object> elementValueSerializer) {

            final Class<?> raw = type.getRawClass();

            if (Stream.class.isAssignableFrom(raw)) {

                final TypeFactory typeFactory = config.getTypeFactory();

                final JavaType[] params = typeFactory.findTypeParameters(type, Stream.class);

                final JavaType vt = (params == null || params.length != 1) ? TypeFactory.unknownType() : params[0];

                return new StreamSerializer(type.getContentType(), usesStaticTyping(config, beanDesc, null),
                        BeanSerializerFactory.instance.createTypeSerializer(config, vt));
            }

            return null;
        }

        /**
         * Uses static typing. Copied from {@link BasicSerializerFactory}
         *
         * @param config the config
         * @param beanDesc the bean desc
         * @param typeSer the type ser
         * @return true, if successful
         */
        private static final boolean usesStaticTyping(final SerializationConfig config, final BeanDescription beanDesc,
                final TypeSerializer typeSer) {
            /*
             * 16-Aug-2010, tatu: If there is a (value) type serializer, we can not force
             * static typing; that would make it impossible to handle expected subtypes
             */
            if (typeSer != null) {
                return false;
            }
            final AnnotationIntrospector intr = config.getAnnotationIntrospector();
            final JsonSerialize.Typing t = intr.findSerializationTyping(beanDesc.getClassInfo());
            if (t != null && t != JsonSerialize.Typing.DEFAULT_TYPING) {
                return (t == JsonSerialize.Typing.STATIC);
            }
            return config.isEnabled(MapperFeature.USE_STATIC_TYPING);
        }

        /**
         * The Class StreamSerializer.
         */
        public static final class StreamSerializer extends AsArraySerializerBase<Stream<?>> {

            /** The Constant serialVersionUID. */
            private static final long serialVersionUID = -455534622397905995L;

            /**
             * Instantiates a new stream serializer.
             *
             * @param elemType the elem type
             * @param staticTyping the static typing
             * @param vts the vts
             */
            public StreamSerializer(final JavaType elemType, final boolean staticTyping, final TypeSerializer vts) {
                super(Stream.class, elemType, staticTyping, vts, null);
            }

            /**
             * Instantiates a new stream serializer.
             *
             * @param src the src
             * @param property the property
             * @param vts the vts
             * @param valueSerializer the value serializer
             */
            public StreamSerializer(final StreamSerializer src, final BeanProperty property, final TypeSerializer vts,
                    final JsonSerializer<?> valueSerializer) {
                super(src, property, vts, valueSerializer, false);
            }

            @Override
            public void serialize(final Stream<?> value, final JsonGenerator gen, final SerializerProvider provider)
                    throws IOException {
                this.serializeContents(value, gen, provider);
            }

            /**
             * withResolved.
             *
             * @param property the property
             * @param vts the vts
             * @param elementSerializer the element serializer
             * @param unwrapSingle ignored always false since streams are one time use I don't believe we can get a
             *            single element
             * @return the as array serializer base
             */
            @Override
            public StreamSerializer withResolved(final BeanProperty property, final TypeSerializer vts,
                    final JsonSerializer<?> elementSerializer, final Boolean unwrapSingle) {
                return new StreamSerializer(this, property, vts, elementSerializer);
            }

            @Override
            protected void serializeContents(final Stream<?> value, final JsonGenerator gen,
                                             final SerializerProvider provider) throws IOException {

                // Stream needs to be closed to prevent resource leaks.
                try (Stream<?> stream = value) {
                    provider.findValueSerializer(Iterator.class, null)
                            .serialize(value.iterator(), gen, provider);
                }
            }

            @Override
            public boolean hasSingleElement(final Stream<?> value) {
                // no really good way to determine (without consuming stream), so:
                return false;
            }

            @Override
            protected StreamSerializer _withValueTypeSerializer(final TypeSerializer vts) {

                return new StreamSerializer(this, this._property, vts, this._elementSerializer);
            }
        }
    }
}