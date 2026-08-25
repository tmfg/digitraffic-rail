package fi.livi.rata.avoindata.updater.service.netex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService.NeTExDatedServiceJourney;
import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService.NeTExLine;
import fi.livi.rata.avoindata.updater.service.netex.NeTExEntityService.NeTExServiceJourney;
import fi.livi.rata.avoindata.updater.service.netex.NeTExRouteData.NeTExJourneyPattern;
import fi.livi.rata.avoindata.updater.service.netex.NeTExRouteData.NeTExRoute;

class NeTExDatasetPartitionTest {

    private static final String LINE_IC = "FTR:Line:IC-1";
    private static final String LINE_Z = "FTR:Line:Z";

    @Test
    void givenTwoLines_whenPartitioning_thenEntitiesLandOnTheirOwnLine() {
        final var partition = NeTExDatasetPartition.partition(
                List.of(line(LINE_IC, "IC 1"), line(LINE_Z, "Z")),
                routeData(List.of(route("FTR:Route:IC-1-a", LINE_IC), route("FTR:Route:Z-a", LINE_Z)),
                        List.of(pattern("FTR:JourneyPattern:IC-1-a", "FTR:Route:IC-1-a"),
                                pattern("FTR:JourneyPattern:Z-a", "FTR:Route:Z-a"))),
                List.of(journey("FTR:ServiceJourney:1", LINE_IC), journey("FTR:ServiceJourney:2", LINE_Z)),
                List.of(dated("FTR:DatedServiceJourney:1", "FTR:ServiceJourney:1"),
                        dated("FTR:DatedServiceJourney:2", "FTR:ServiceJourney:2")));

        assertEquals(2, partition.lineSlices().size());
        final var ic = partition.lineSlices().get(0);
        assertEquals(LINE_IC, ic.line().id());
        assertEquals(List.of("FTR:Route:IC-1-a"), ic.routes().stream().map(NeTExRoute::id).toList());
        assertEquals(List.of("FTR:JourneyPattern:IC-1-a"),
                ic.journeyPatterns().stream().map(NeTExJourneyPattern::id).toList());
        assertEquals(List.of("FTR:ServiceJourney:1"),
                ic.serviceJourneys().stream().map(NeTExServiceJourney::id).toList());
        assertEquals(List.of("FTR:DatedServiceJourney:1"),
                ic.datedServiceJourneys().stream().map(NeTExDatedServiceJourney::id).toList());
        assertTrue(partition.orphans().isEmpty());
    }

    @Test
    void givenEveryEntity_whenPartitioning_thenPartitionIsTotalAndDisjoint() {
        final var routes = List.of(route("FTR:Route:IC-1-a", LINE_IC), route("FTR:Route:IC-1-b", LINE_IC),
                route("FTR:Route:Z-a", LINE_Z));
        final var journeys = List.of(journey("FTR:ServiceJourney:1", LINE_IC),
                journey("FTR:ServiceJourney:2", LINE_IC), journey("FTR:ServiceJourney:3", LINE_Z));

        final var partition = NeTExDatasetPartition.partition(
                List.of(line(LINE_IC, "IC 1"), line(LINE_Z, "Z")),
                routeData(routes, List.of()), journeys, List.of());

        final var assignedRoutes = partition.lineSlices().stream().flatMap(s -> s.routes().stream())
                .map(NeTExRoute::id).toList();
        assertEquals(routes.size(), assignedRoutes.size());
        assertEquals(assignedRoutes.size(), Set.copyOf(assignedRoutes).size());

        final var assignedJourneys = partition.lineSlices().stream().flatMap(s -> s.serviceJourneys().stream())
                .map(NeTExServiceJourney::id).toList();
        assertEquals(journeys.size(), assignedJourneys.size());
        assertEquals(assignedJourneys.size(), Set.copyOf(assignedJourneys).size());
    }

    @Test
    void givenDatedJourneyWithUnknownServiceJourney_whenPartitioning_thenReportedAsOrphan() {
        final var partition = NeTExDatasetPartition.partition(
                List.of(line(LINE_IC, "IC 1")),
                routeData(List.of(route("FTR:Route:IC-1-a", LINE_IC)), List.of()),
                List.of(journey("FTR:ServiceJourney:1", LINE_IC)),
                List.of(dated("FTR:DatedServiceJourney:1", "FTR:ServiceJourney:1"),
                        dated("FTR:DatedServiceJourney:99", "FTR:ServiceJourney:missing")));

        assertEquals(1, partition.lineSlices().get(0).datedServiceJourneys().size());
        assertEquals(1, partition.orphans().datedServiceJourneys().size());
        assertEquals("FTR:DatedServiceJourney:99",
                partition.orphans().datedServiceJourneys().get(0).id());
        assertEquals(1, partition.orphans().total());
    }

    @Test
    void givenRouteForUnknownLine_whenPartitioning_thenRouteAndItsPatternAreOrphans() {
        final var partition = NeTExDatasetPartition.partition(
                List.of(line(LINE_IC, "IC 1")),
                routeData(List.of(route("FTR:Route:ghost-a", "FTR:Line:ghost")),
                        List.of(pattern("FTR:JourneyPattern:ghost-a", "FTR:Route:ghost-a"))),
                List.of(), List.of());

        assertEquals(1, partition.orphans().routes().size());
        assertEquals(1, partition.orphans().journeyPatterns().size());
        assertTrue(partition.lineSlices().get(0).routes().isEmpty());
    }

    @Test
    void givenLineWithNoJourneys_whenPartitioning_thenSliceStillPresentAndEmpty() {
        final var partition = NeTExDatasetPartition.partition(
                List.of(line(LINE_IC, "IC 1"), line(LINE_Z, "Z")),
                routeData(List.of(route("FTR:Route:IC-1-a", LINE_IC)), List.of()),
                List.of(journey("FTR:ServiceJourney:1", LINE_IC)), List.of());

        final var z = partition.lineSlices().get(1);
        assertEquals(LINE_Z, z.line().id());
        assertTrue(z.routes().isEmpty());
        assertTrue(z.serviceJourneys().isEmpty());
    }

    // --- Helpers ---

    private static NeTExLine line(final String id, final String publicCode) {
        return new NeTExLine(id, publicCode, publicCode, publicCode, "FTR:Operator:vr", "rail");
    }

    private static NeTExRoute route(final String id, final String lineRef) {
        return new NeTExRoute(id, "A - B", lineRef, List.of());
    }

    private static NeTExJourneyPattern pattern(final String id, final String routeRef) {
        return new NeTExJourneyPattern(id, routeRef, List.of());
    }

    private static NeTExServiceJourney journey(final String id, final String lineRef) {
        return new NeTExServiceJourney(id, "name", "code", "FTR:JourneyPattern:x",
                "FTR:Operator:vr", lineRef, List.of());
    }

    private static NeTExDatedServiceJourney dated(final String id, final String serviceJourneyRef) {
        return new NeTExDatedServiceJourney(id, serviceJourneyRef, LocalDate.of(2026, 8, 21));
    }

    private static NeTExRouteData routeData(final List<NeTExRoute> routes,
            final List<NeTExJourneyPattern> patterns) {
        return new NeTExRouteData(routes, patterns, Map.of());
    }
}
