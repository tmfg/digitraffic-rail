package fi.livi.rata.avoindata.updater.deserializers;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public abstract class AEntityDeserializer<T> extends JsonDeserializer<T> {
    protected <T> List<T> getObjectsFromNode(final JsonParser jsonParser, final JsonNode node, final Class<T[]> objectClass,
            final String nodeName) throws IOException {
        return Arrays.asList(jsonParser.getCodec().readValue(node.get(nodeName).traverse(jsonParser.getCodec()), objectClass));
    }

    protected <T> T getObjectFromNode(final JsonParser jp, final JsonNode node, final String nodeName,
            final Class<? extends T> objectClass) throws IOException {
        return jp.getCodec().readValue(node.get(nodeName).traverse(jp.getCodec()), objectClass);
    }

    protected Boolean nullIfFalse(JsonNode value) {
        if (!value.asBoolean()) {
            return null;
        }

        return Boolean.TRUE;
    }

    protected Boolean isNodeNull(JsonNode value) {
        return value == null || value.isNull();
    }

    protected LocalDate getNodeAsLocalDate(final JsonNode node) {
        if (node == null) {
            return null;
        } else {
            return LocalDate.parse(node.asText());
        }
    }

    protected ZonedDateTime getNodeAsDateTime(final JsonNode node) {
        if (node == null) {
            return null;
        }
        return ZonedDateTime.parse(node.asText());
    }

    protected LocalDateTime getNodeAsLocalDateTime(final JsonNode node) {
        if (node == null) {
            return null;
        }
        return LocalDateTime.parse(node.asText());
    }

    protected Boolean getNullableBoolean(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode stringNode = node.get(nodeName);
        if (stringNode == null) {
            return null;
        }

        return stringNode.asBoolean();
    }

    protected String getNullableString(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode stringNode = node.get(nodeName);
        if (stringNode == null) {
            return null;
        }

        return stringNode.asText();
    }

    protected String getStringFromNode(JsonNode node, String nodeName) {
        return node.get(nodeName).asText();
    }

    protected LocalTime getLocalTimeFromNode(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode stringNode = node.get(nodeName);
        if (stringNode == null || stringNode.asText() == null || stringNode.asText().equals("null")) {
            return null;
        }

        return LocalTime.parse(stringNode.asText());
    }
}
