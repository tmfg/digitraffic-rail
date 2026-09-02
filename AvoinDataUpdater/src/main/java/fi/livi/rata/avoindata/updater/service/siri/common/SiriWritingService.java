package fi.livi.rata.avoindata.updater.service.siri.common;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import org.springframework.stereotype.Service;

import uk.org.siri.siri21.RequestorRef;
import uk.org.siri.siri21.ServiceDelivery;
import uk.org.siri.siri21.Siri;

/**
 * Marshals SIRI 2.1 envelopes to XML and structurally validates them.
 */
@Service
public class SiriWritingService {

    private volatile JAXBContext jaxbContext;

    private JAXBContext getJaxbContext() {
        if (jaxbContext == null) {
            synchronized (this) {
                if (jaxbContext == null) {
                    try {
                        jaxbContext = JAXBContext.newInstance(Siri.class);
                    } catch (final JAXBException e) {
                        throw new SiriMarshalException("Failed to initialize JAXB context", e);
                    }
                }
            }
        }
        return jaxbContext;
    }

    public Siri buildEnvelope(final ZonedDateTime responseTimestamp, final String producerRef) {
        final Siri siri = new Siri();
        siri.setVersion("2.1");

        final ServiceDelivery serviceDelivery = new ServiceDelivery();
        serviceDelivery.setResponseTimestamp(
                responseTimestamp.withZoneSameInstant(SiriTimeConverter.HELSINKI_ZONE));

        final RequestorRef ref = new RequestorRef();
        ref.setValue(producerRef);
        serviceDelivery.setProducerRef(ref);

        siri.setServiceDelivery(serviceDelivery);
        return siri;
    }

    public String marshalToXml(final Siri siri) {
        try {
            final Marshaller marshaller = getJaxbContext().createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            marshaller.setAdapter(org.w3._2001.xmlschema.Adapter1.class,
                    new org.w3._2001.xmlschema.Adapter1() {
                        @Override
                        public String marshal(final ZonedDateTime v) {
                            if (v == null) return null;
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

    // Structural (JAXB-binding) check only: well-formedness + bindability, not XSD/cardinality. Entur's
    // SiriValidator is built/tested on Java 11 (their CI pins java-version: 11); on our JDK 25 its bundled-XSD
    // loading fails (src-import.3.1 on the xml namespace import), so real XSD/profile validation stays with VACO.
    public boolean isSchemaValid(final byte[] xml) {
        try {
            final Object root = getJaxbContext().createUnmarshaller()
                    .unmarshal(new ByteArrayInputStream(xml));
            return root instanceof Siri
                    || (root instanceof JAXBElement<?> element && element.getValue() instanceof Siri);
        } catch (final JAXBException e) {
            return false;
        }
    }
}
