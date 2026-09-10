package fi.livi.rata.avoindata.updater.service.siri.common;

/**
 * A journey's first/last commercial stop, as published in NeTEx: the station short code and its planned track,
 * from which {@link SiriStopResolver} can resolve a PETI quay. Used to derive SIRI-VM's optional
 * {@code OriginRef}/{@code DestinationRef} — SIRI-ET derives its own origin/destination directly from the live
 * stop sequence instead, since it already builds a full {@code EtCall} list.
 */
public record JourneyEndpoint(String stationShortCode, String plannedTrack) {}
