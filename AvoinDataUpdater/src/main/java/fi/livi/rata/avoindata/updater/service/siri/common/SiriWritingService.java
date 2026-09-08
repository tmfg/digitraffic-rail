package fi.livi.rata.avoindata.updater.service.siri.common;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.ValidationEvent;

import org.entur.siri.validator.SiriValidationEventHandler;
import org.entur.siri.validator.SiriValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import fi.livi.rata.avoindata.common.utils.DateProvider;
import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;

/**
 * Marshals SIRI envelopes to XML and validates them against the SIRI <strong>2.0</strong> XML Schema (via
 * Entur's {@link SiriValidator}). Uses the SIRI 2.1 JAXB bindings (a backward-compatible superset) but declares
 * {@code version="2.0"} and validates against the 2.0 schema, so any 2.1-only element we accidentally emit is
 * rejected before publishing.
 */
@Service
public class SiriWritingService {

    private static final Logger log = LoggerFactory.getLogger(SiriWritingService.class);

    private final JAXBContext jaxbContext;

    public SiriWritingService() {
        try {
            this.jaxbContext = JAXBContext.newInstance(Siri.class);
        } catch (final JAXBException e) {
            throw new SiriMarshalException("Failed to initialize JAXB context", e);
        }
    }

    public Siri buildEnvelope(final ZonedDateTime responseTimestamp, final String producerRef) {
        final Siri siri = new Siri();
        // Nordic SIRI profile is based on the SIRI 2.0 XML Schema and we emit no 2.1-only fields, so the
        // envelope declares 2.0 (matching the EstimatedTimetableDelivery version).
        siri.setVersion("2.0");

        final ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.setResponseTimestamp(
                responseTimestamp.withZoneSameInstant(DateProvider.ZONE_ID_HKI));

        final RequestorRef ref = new RequestorRef();
        ref.setValue(producerRef);
        serviceDelivery.setProducerRef(ref);

        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    public String marshalToXml(final Siri siri) {
        try {
            final Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            marshaller.setAdapter(org.w3._2001.xmlschema.Adapter1.class,
                    new org.w3._2001.xmlschema.Adapter1() {
                        @Override
                        public String marshal(final ZonedDateTime v) {
                            if (v == null) {
                                return null;
                            }
                            return v.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        }
                    });
            final StringWriter writer = new StringWriter();
            marshaller.marshal(siri, writer);
            return writer.toString();
        } catch (final JAXBException e) {
            throw new SiriMarshalException("Failed to marshal SIRI XML", e);
        }
    }

    public byte[] marshalToBytes(final Siri siri) {
        return marshalToXml(siri).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Validates the marshalled document against the SIRI <strong>2.0</strong> XML Schema (Entur
     * {@link SiriValidator}, {@code VERSION_2_0}). Returns {@code false} (and logs each event) on any schema
     * violation, so a 2.1-only element or a cardinality breach is never published.
     */
    public boolean isSchemaValid(final byte[] xml) {
        final String xmlString = new String(xml, StandardCharsets.UTF_8);
        try {
            final SiriValidationEventHandler handler =
                    SiriValidator.validateAndGetHandler(xmlString, SiriValidator.Version.VERSION_2_0);
            if (!handler.isValid()) {
                for (final ValidationEvent event : handler.events) {
                    log.warn("event=rail.siri.validation outcome=invalid severity={} message=\"{}\"",
                            event.getSeverity(), event.getMessage());
                }
                return false;
            }
            return true;
        } catch (final JAXBException | SAXException e) {
            log.error("event=rail.siri.validation outcome=error — SIRI 2.0 schema validation could not run", e);
            return false;
        }
    }
}
