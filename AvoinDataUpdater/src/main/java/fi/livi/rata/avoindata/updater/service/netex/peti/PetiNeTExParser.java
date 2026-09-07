package fi.livi.rata.avoindata.updater.service.netex.peti;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parses a PETI rail NeTEx stops.xml into a list of {@link PetiStop} records.
 *
 * <p>
 * Two distinct error levels:
 * <ul>
 * <li><b>Document-level failure</b> (malformed/truncated XML) throws
 * {@link PetiParseException}, so the
 * caller keeps the last-good snapshot rather than persisting partial/empty
 * data.</li>
 * <li><b>Individual StopPlace</b> with a missing or non-numeric uicCode is
 * skipped (and left out of the
 * result) rather than failing the whole parse</li>
 * </ul>
 */
@Component
public class PetiNeTExParser {

    private static final Logger log = LoggerFactory.getLogger(PetiNeTExParser.class);
    private static final String NETEX_NS = "http://www.netex.org.uk/netex";

    /**
     * Parse stops from a PETI NeTEx stops.xml InputStream.
     *
     * @param stopsXml InputStream of stops.xml content
     * @return list of successfully parsed PetiStop records (StopPlaces with
     *         missing/malformed uicCode omitted)
     * @throws PetiParseException if the document cannot be parsed
     *                            (malformed/truncated XML)
     */
    public List<PetiStop> parse(final InputStream stopsXml) {
        final List<PetiStop> result = new ArrayList<>();
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Disable external entities to prevent XXE
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(stopsXml);

            final NodeList stopPlaces = doc.getElementsByTagNameNS(NETEX_NS, "StopPlace");
            for (int i = 0; i < stopPlaces.getLength(); i++) {
                final Element spElement = (Element) stopPlaces.item(i);
                final PetiStop stop = parseStopPlace(spElement);
                if (stop != null) {
                    result.add(stop);
                }
            }
        } catch (final Exception e) {
            // Document-level failure: do not return partial/empty data — fail fast so the
            // caller
            // keeps the last-good PETI snapshot instead of persisting a degraded state.
            log.error("Failed to parse PETI stops.xml", e);
            throw new PetiParseException("Failed to parse PETI stops.xml", e);
        }
        return result;
    }

    private PetiStop parseStopPlace(final Element spElement) {
        final String stopPlaceId = spElement.getAttribute("id");
        final String name = getDirectChildText(spElement, "Name");

        // Parse keyList
        Integer uicCode = null;
        boolean parentStopPlace = false;

        final Element keyListEl = getDirectChildElement(spElement, "keyList");
        if (keyListEl != null) {
            final NodeList keyValues = keyListEl.getElementsByTagNameNS(NETEX_NS, "KeyValue");
            for (int i = 0; i < keyValues.getLength(); i++) {
                final Element kv = (Element) keyValues.item(i);
                final String key = getDirectChildText(kv, "Key");
                final String value = getDirectChildText(kv, "Value");
                if ("uicCode".equals(key)) {
                    try {
                        uicCode = Integer.parseInt(value);
                    } catch (final NumberFormatException e) {
                        return null; // malformed uicCode → skip
                    }
                } else if ("IS_PARENT_STOP_PLACE".equals(key)) {
                    parentStopPlace = "true".equalsIgnoreCase(value);
                }
            }
        }

        if (uicCode == null) {
            return null; // missing uicCode → skip
        }

        // Parse accessibility at StopPlace level
        final PetiAccessibility accessibility = parseAccessibility(spElement);

        // Parse quays
        final List<PetiQuay> quays = parseQuays(spElement);

        return new PetiStop(stopPlaceId, uicCode, name, parentStopPlace, accessibility, quays);
    }

    private List<PetiQuay> parseQuays(final Element spElement) {
        final List<PetiQuay> quays = new ArrayList<>();
        final Element quaysEl = getDirectChildElement(spElement, "quays");
        if (quaysEl == null) {
            return quays;
        }

        final NodeList quayNodes = quaysEl.getElementsByTagNameNS(NETEX_NS, "Quay");
        for (int i = 0; i < quayNodes.getLength(); i++) {
            final Element quayEl = (Element) quayNodes.item(i);
            final String quayId = quayEl.getAttribute("id");
            final String publicCode = getDirectChildText(quayEl, "PublicCode");
            final PetiAccessibility quayAccessibility = parseAccessibility(quayEl);
            final Element location = centroidLocation(quayEl);
            quays.add(new PetiQuay(quayId, publicCode,
                    coordinate(location, "Latitude"), coordinate(location, "Longitude"),
                    quayAccessibility));
        }
        return quays;
    }

    private Element centroidLocation(final Element quayEl) {
        final Element centroid = getDirectChildElement(quayEl, "Centroid");
        return centroid == null ? null : getDirectChildElement(centroid, "Location");
    }

    private BigDecimal coordinate(final Element location, final String localName) {
        if (location == null) {
            return null;
        }
        final String text = getDirectChildText(location, localName);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (final NumberFormatException e) {
            log.warn("method=parseQuays unparseableCoordinate element={} value={}", localName, text);
            return null;
        }
    }

    private PetiAccessibility parseAccessibility(final Element parent) {
        final Element assessmentEl = getDirectChildElement(parent, "AccessibilityAssessment");
        if (assessmentEl == null) {
            return null;
        }

        final Element limitationsEl = getDirectChildElement(assessmentEl, "limitations");
        if (limitationsEl == null) {
            return null;
        }

        final Element limitationEl = getDirectChildElement(limitationsEl, "AccessibilityLimitation");
        if (limitationEl == null) {
            return null;
        }

        return new PetiAccessibility(
                parseLimitationStatus(getDirectChildText(limitationEl, "WheelchairAccess")),
                parseLimitationStatus(getDirectChildText(limitationEl, "StepFreeAccess")),
                parseLimitationStatus(getDirectChildText(limitationEl, "LiftFreeAccess")),
                parseLimitationStatus(getDirectChildText(limitationEl, "EscalatorFreeAccess")),
                parseLimitationStatus(getDirectChildText(limitationEl, "AudibleSignalsAvailable")),
                parseLimitationStatus(getDirectChildText(limitationEl, "VisualSignsAvailable")));
    }

    private PetiLimitationStatus parseLimitationStatus(final String value) {
        if (value == null) {
            return PetiLimitationStatus.UNKNOWN;
        }
        return switch (value.toLowerCase()) {
            case "true" -> PetiLimitationStatus.TRUE;
            case "false" -> PetiLimitationStatus.FALSE;
            default -> PetiLimitationStatus.UNKNOWN;
        };
    }

    private Element getDirectChildElement(final Element parent, final String localName) {
        final org.w3c.dom.Node child = parent.getFirstChild();
        for (org.w3c.dom.Node n = child; n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && localName.equals(el.getLocalName())) {
                return el;
            }
        }
        return null;
    }

    private String getDirectChildText(final Element parent, final String localName) {
        final Element child = getDirectChildElement(parent, localName);
        if (child == null) {
            return null;
        }
        return child.getTextContent().trim();
    }
}
