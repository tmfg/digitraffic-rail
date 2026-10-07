package fi.livi.rata.avoindata.updater.service.siri.vm;

import uk.org.siri.siri21.Siri;

/** The built SIRI-VM document, its serialized UTF-8 XML bytes, and the per-cycle {@link SiriVmStats}. */
public record SiriVmResult(Siri document, byte[] bytes, SiriVmStats stats) {}
