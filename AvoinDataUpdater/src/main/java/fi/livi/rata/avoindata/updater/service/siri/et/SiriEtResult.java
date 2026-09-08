package fi.livi.rata.avoindata.updater.service.siri.et;

import uk.org.siri.siri21.Siri;

/** The built SIRI-ET document, its serialized UTF-8 XML bytes, and the per-cycle {@link SiriEtStats}. */
public record SiriEtResult(Siri document, byte[] bytes, SiriEtStats stats) {}
