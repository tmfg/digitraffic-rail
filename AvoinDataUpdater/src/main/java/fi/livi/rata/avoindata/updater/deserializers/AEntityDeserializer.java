package fi.livi.rata.avoindata.updater.deserializers;

import static fi.livi.rata.avoindata.updater.service.ruma.RumaUtils.ratakmvaliToString;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.locationtech.jts.geom.Geometry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public abstract class AEntityDeserializer<T> extends JsonDeserializer<T> {

    protected <Type> List<Type> getObjectsFromNode(final JsonParser jsonParser, final JsonNode node, final Class<Type[]> objectClass,
                                                   final String nodeName) throws IOException {
        return Arrays.asList(jsonParser.getCodec().readValue(node.get(nodeName).traverse(jsonParser.getCodec()), objectClass));
    }

    protected <Type> Type getObjectFromNode(final JsonParser jp, final JsonNode node, final String nodeName,
                                            final Class<? extends Type> objectClass) throws IOException {
        return jp.getCodec().readValue(node.get(nodeName).traverse(jp.getCodec()), objectClass);
    }

    protected Boolean nullIfFalse(final JsonNode value) {
        if (!value.asBoolean()) {
            return null;
        }

        return Boolean.TRUE;
    }

    protected Boolean nullIfFalse(final Boolean value) {
        if (value == null || !value) {
            return null;
        }
        return Boolean.TRUE;
    }

    protected Boolean isNodeNull(final JsonNode value) {
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
        if (isNodeNull(node)) {
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

    protected Integer getNullableInteger(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode stringNode = node.get(nodeName);
        if (stringNode == null) {
            return null;
        }

        return stringNode.asInt();
    }

    protected Double getNullableDouble(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode doubleNode = node.get(nodeName);
        if (isNodeNull(doubleNode)) {
            return null;
        }

        return doubleNode.asDouble();
    }

    protected String getNullableString(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode stringNode = node.get(nodeName);
        if (isNodeNull(stringNode)) {
            return null;
        }

        return stringNode.asText();
    }

    protected String getNullableRumaRatakmvali(final JsonNode node, final String nodeName) {
        if (node == null) {
            return null;
        }

        final JsonNode ratakmvaliNode = node.get(nodeName);
        if (isNodeNull(ratakmvaliNode)) {
            return null;
        }

        final String ratanumero = ratakmvaliNode.get("ratanumero").asText();
        final JsonNode alkuNode = ratakmvaliNode.get("alku");
        final int alkuRatakm = alkuNode.get("ratakm").asInt();
        final int alkuEtaisyys = alkuNode.get("etaisyys").asInt();
        final JsonNode loppuNode = ratakmvaliNode.get("loppu");
        final int loppuRatakm = loppuNode.get("ratakm").asInt();
        final int loppuEtaisyys = loppuNode.get("etaisyys").asInt();

        return ratakmvaliToString(ratanumero, alkuRatakm, alkuEtaisyys, loppuRatakm, loppuEtaisyys);
    }

    protected String getStringFromNode(final JsonNode node, final String nodeName) {
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

    protected Geometry deserializeGeometry(final JsonNode node, final JsonParser jsonParser) throws IOException {
        return jsonParser.getCodec().readValue(node.traverse(jsonParser.getCodec()), Geometry.class);
    }

}
