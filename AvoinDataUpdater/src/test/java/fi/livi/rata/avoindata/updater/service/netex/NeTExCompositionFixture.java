package fi.livi.rata.avoindata.updater.service.netex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.rutebanken.netex.model.PublicationDeliveryStructure;

/**
 * Builds a minimal dataset around a set of compositions so composition output
 * can be asserted after it has been merged into the line files. Synthesises the
 * Line, JourneyPattern and journeys each composition needs in order to be
 * claimed by a Line rather than dropped as unmatched.
 */
final class NeTExCompositionFixture {

        private final NeTExIdGenerator idGenerator;
        private final NeTExWritingService writingService;

        NeTExCompositionFixture(final NeTExIdGenerator idGenerator) {
                this.idGenerator = idGenerator;
                this.writingService = new NeTExWritingService(idGenerator,
                                new NeTExCompositionWritingService(idGenerator));
        }

        /**
         * Returns every file of the dataset concatenated. Placement of individual
         * elements is asserted by NeTExDatasetLayoutTest; these tests only care that
         * the composition survived the merge.
         */
        String buildDatasetXml(final NeTExCompositionService.CompositionData compositions) {
                final byte[] zip = writingService.marshalAndZip(buildDataset(compositions));
                final StringBuilder joined = new StringBuilder();
                try (final ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
                        ZipEntry entry;
                        while ((entry = zis.getNextEntry()) != null) {
                                joined.append(new String(zis.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
                        }
                } catch (final IOException e) {
                        throw new RuntimeException("Failed to read generated dataset", e);
                }
                return joined.toString();
        }

        byte[] marshalDataset(final NeTExCompositionService.CompositionData compositions) {
                return writingService.marshalAndZip(buildDataset(compositions));
        }

        Map<String, PublicationDeliveryStructure> buildDataset(
                        final NeTExCompositionService.CompositionData compositions) {
                final String lineId = idGenerator.lineId("IC");
                final String journeyPatternId = idGenerator.journeyPatternId("IC", "a");
                final String routeId = idGenerator.routeId("IC", "a");

                final List<NeTExEntityService.NeTExServiceJourney> serviceJourneys = new ArrayList<>();
                final List<NeTExEntityService.NeTExDatedServiceJourney> datedServiceJourneys = new ArrayList<>();
                for (final var dj : compositions.datedJourneys()) {
                        final String serviceJourneyId = idGenerator.serviceJourneyId(dj.trainNumber(), 1L);
                        final String datedId = idGenerator.datedServiceJourneyId(dj.trainNumber(), dj.date());
                        if (datedServiceJourneys.stream().anyMatch(existing -> existing.id().equals(datedId))) {
                                continue;
                        }
                        serviceJourneys.add(new NeTExEntityService.NeTExServiceJourney(
                                        serviceJourneyId, "IC " + dj.trainNumber(), String.valueOf(dj.trainNumber()),
                                        journeyPatternId, idGenerator.operatorId("vr"), lineId,
                                        List.of(new NeTExEntityService.NeTExPassingTime(1, null, "05:30:00", null, null,
                                                        idGenerator.stopPointInJourneyPatternId(journeyPatternId, 1)),
                                                        new NeTExEntityService.NeTExPassingTime(2, "11:30:00", null,
                                                                        null, null,
                                                                        idGenerator.stopPointInJourneyPatternId(
                                                                                        journeyPatternId, 2)))));
                        datedServiceJourneys.add(new NeTExEntityService.NeTExDatedServiceJourney(
                                        datedId, serviceJourneyId, dj.date()));
                }

                final NeTExStopsData stopsData = new NeTExStopsData(
                                List.of(new NeTExStopsData.NeTExScheduledStopPoint(
                                                idGenerator.scheduledStopPointId("HKI"),
                                                "Helsinki", "HKI", new BigDecimal("60.172133"),
                                                new BigDecimal("24.941662")),
                                                new NeTExStopsData.NeTExScheduledStopPoint(
                                                                idGenerator.scheduledStopPointId("OL"),
                                                                "Oulu", "OL", new BigDecimal("65.011900"),
                                                                new BigDecimal("25.483800"))),
                                List.of(new NeTExStopsData.NeTExRoutePoint(idGenerator.routePointId("HKI"), "HKI"),
                                                new NeTExStopsData.NeTExRoutePoint(idGenerator.routePointId("OL"),
                                                                "OL")),
                                List.of(new NeTExStopsData.NeTExDestinationDisplay(
                                                idGenerator.destinationDisplayId("OL"), "Oulu")));

                final NeTExRouteData routeData = new NeTExRouteData(
                                List.of(new NeTExRouteData.NeTExRoute(routeId, "HKI - OL", lineId,
                                                List.of(idGenerator.routePointId("HKI"),
                                                                idGenerator.routePointId("OL")))),
                                List.of(new NeTExRouteData.NeTExJourneyPattern(journeyPatternId, routeId,
                                                List.of(new NeTExRouteData.NeTExStopPointInPattern(1,
                                                                idGenerator.scheduledStopPointId("HKI"), true, false,
                                                                idGenerator.destinationDisplayId("OL")),
                                                                new NeTExRouteData.NeTExStopPointInPattern(2,
                                                                                idGenerator.scheduledStopPointId("OL"),
                                                                                false, true, null)))),
                                Map.of(1L, journeyPatternId));

                final var lines = List.of(new NeTExEntityService.NeTExLine(lineId, "Helsinki-Oulu", "IC", "IC",
                                idGenerator.operatorId("vr"), "rail"));
                final var operators = List.of(new NeTExEntityService.NeTExOperator(idGenerator.operatorId("vr"),
                                "VR", "vr", 10));

                return writingService.buildDataset(stopsData, routeData, lines, operators,
                                serviceJourneys, datedServiceJourneys, compositions,
                                ZonedDateTime.of(2026, 6, 30, 4, 0, 0, 0, ZoneOffset.UTC));
        }

        static LocalDate date(final String iso) {
                return LocalDate.parse(iso);
        }
}
