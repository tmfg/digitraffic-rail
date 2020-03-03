package fi.livi.rata.avoindata.updater.deserializers;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.vividsolutions.jts.geom.Geometry;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public abstract class AEntityDeserializer<T> extends JsonDeserializer<T> {

    private static final String INFRA_OID_PREFIX = "x.x.xxx.LIVI.INFRA.";
    private static final String INFRA_OID_PREFIX_NEW = "1.2.246.586.1.";

    private static final String JETI_OID_PREFIX = "x.x.xxx.LIVI.ETJ2.";
    private static final String JETI_OID_PREFIX_NEW = "1.2.246.586.2.";

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

    protected static String ratakmvaliToString(
            final String ratanumero,
            final int alkuRatakm,
            final int alkuEtaisyys,
            final int loppuRatakm,
            final int loppuEtaisyys) {
        return String.format(Locale.ROOT, "(%s) %d+%04d > %d+%04d", ratanumero, alkuRatakm, alkuEtaisyys, loppuRatakm, loppuEtaisyys);
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

    protected Geometry deserializeGeometry(JsonNode node, JsonParser jsonParser) throws IOException {
        return jsonParser.getCodec().readValue(node.traverse(jsonParser.getCodec()), Geometry.class);
    }

    protected static String normalizeTrakediaInfraOid(String oid) {
        if (oid == null) {
            return null;
        } else if (oid.startsWith(INFRA_OID_PREFIX_NEW)) {
            return oid;
        } else {
            return INFRA_OID_PREFIX_NEW + oid.substring(INFRA_OID_PREFIX.length());
        }
    }

    protected static String normalizeJetiOid(String oid) {
        if (oid == null) {
            return null;
        } else if (oid.startsWith(JETI_OID_PREFIX_NEW)) {
            return oid;
        } else {
            return JETI_OID_PREFIX_NEW + oid.substring(JETI_OID_PREFIX.length());
        }
    }
}
