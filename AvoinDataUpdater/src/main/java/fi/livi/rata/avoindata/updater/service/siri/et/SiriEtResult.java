package fi.livi.rata.avoindata.updater.service.siri.et;

import uk.org.siri.siri21.Siri;

/** The built SIRI-ET document plus the per-cycle {@link SiriEtStats} for the generation wide event. */
public record SiriEtResult(Siri document, SiriEtStats stats) {}
