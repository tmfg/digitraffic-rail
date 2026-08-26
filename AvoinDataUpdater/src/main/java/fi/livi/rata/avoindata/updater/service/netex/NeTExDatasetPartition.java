package fi.livi.rata.avoindata.updater.service.netex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService.NeTExLine;
import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService.NeTExServiceJourney;
import fi.livi.rata.avoindata.updater.service.netex.NeTExRouteData.NeTExJourneyPattern;
import fi.livi.rata.avoindata.updater.service.netex.NeTExRouteData.NeTExRoute;

/**
 * Splits the dataset into one slice per Line, leaving cross-line entities
 * (stops, calendar, organisations) to the shared file. Entities whose Line
 * cannot be resolved are returned as orphans instead of being dropped here, so
 * the caller can log how many were discarded — a handful is expected, a large
 * number means the upstream data is inconsistent.
 */
public record NeTExDatasetPartition(List<LineSlice> lineSlices, Orphans orphans) {

    public record LineSlice(NeTExLine line,
            List<NeTExRoute> routes,
            List<NeTExJourneyPattern> journeyPatterns,
            List<NeTExServiceJourney> serviceJourneys) {
    }

    public record Orphans(List<NeTExRoute> routes,
            List<NeTExJourneyPattern> journeyPatterns,
            List<NeTExServiceJourney> serviceJourneys) {

        public boolean isEmpty() {
            return routes.isEmpty() && journeyPatterns.isEmpty() && serviceJourneys.isEmpty();
        }

        public int total() {
            return routes.size() + journeyPatterns.size() + serviceJourneys.size();
        }
    }

    public static NeTExDatasetPartition partition(final List<NeTExLine> lines,
            final NeTExRouteData routeData,
            final List<NeTExServiceJourney> serviceJourneys) {

        final Map<String, List<NeTExRoute>> routesByLine = new HashMap<>();
        final List<NeTExRoute> orphanRoutes = new ArrayList<>();
        final Set<String> knownLineIds = new HashSet<>();
        lines.forEach(line -> knownLineIds.add(line.id()));
        for (final NeTExRoute route : routeData.getRoutes()) {
            if (knownLineIds.contains(route.lineRef())) {
                routesByLine.computeIfAbsent(route.lineRef(), k -> new ArrayList<>()).add(route);
            } else {
                orphanRoutes.add(route);
            }
        }

        // JourneyPatterns reach their Line only through the Route they follow.
        final Map<String, String> lineIdByRouteId = new HashMap<>();
        routesByLine.forEach((lineId, routes) -> routes.forEach(route -> lineIdByRouteId.put(route.id(), lineId)));
        final Map<String, List<NeTExJourneyPattern>> patternsByLine = new HashMap<>();
        final List<NeTExJourneyPattern> orphanPatterns = new ArrayList<>();
        for (final NeTExJourneyPattern pattern : routeData.getJourneyPatterns()) {
            final String lineId = lineIdByRouteId.get(pattern.routeRef());
            if (lineId != null) {
                patternsByLine.computeIfAbsent(lineId, k -> new ArrayList<>()).add(pattern);
            } else {
                orphanPatterns.add(pattern);
            }
        }

        final Map<String, List<NeTExServiceJourney>> journeysByLine = new HashMap<>();
        final List<NeTExServiceJourney> orphanJourneys = new ArrayList<>();
        for (final NeTExServiceJourney journey : serviceJourneys) {
            if (knownLineIds.contains(journey.lineRef())) {
                journeysByLine.computeIfAbsent(journey.lineRef(), k -> new ArrayList<>()).add(journey);
            } else {
                orphanJourneys.add(journey);
            }
        }

        final Map<String, LineSlice> slices = new LinkedHashMap<>();
        for (final NeTExLine line : lines) {
            slices.put(line.id(), new LineSlice(line,
                    routesByLine.getOrDefault(line.id(), List.of()),
                    patternsByLine.getOrDefault(line.id(), List.of()),
                    journeysByLine.getOrDefault(line.id(), List.of())));
        }

        return new NeTExDatasetPartition(List.copyOf(slices.values()),
                new Orphans(orphanRoutes, orphanPatterns, orphanJourneys));
    }
}
