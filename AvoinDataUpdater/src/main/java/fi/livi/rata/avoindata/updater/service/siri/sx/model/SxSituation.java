package fi.livi.rata.avoindata.updater.service.siri.sx.model;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * One situation of the SIRI-SX feed, ready to be written. {@code endTime} is always set.
 *
 * @param versionedAtTime creation time of the latest version, {@code creationTime} that of the first version
 */
public record SxSituation(String situationNumber, int version, ZonedDateTime creationTime,
                          ZonedDateTime versionedAtTime, boolean closed, ZonedDateTime startTime,
                          ZonedDateTime endTime, SxReportType reportType, List<SxText> summaries,
                          List<SxText> descriptions, SxTarget target) {}
