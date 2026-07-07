package fi.livi.rata.avoindata.updater.service.netex;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fi.livi.rata.avoindata.common.dao.composition.CompositionRepository;
import fi.livi.rata.avoindata.common.dao.gtfs.GeneratedExportRepository;
import fi.livi.rata.avoindata.common.domain.composition.Composition;
import fi.livi.rata.avoindata.common.domain.composition.JourneySection;
import fi.livi.rata.avoindata.common.domain.composition.Locomotive;
import fi.livi.rata.avoindata.common.domain.composition.Wagon;
import fi.livi.rata.avoindata.common.domain.gtfs.GeneratedExport;
import fi.livi.rata.avoindata.common.utils.DateProvider;

/**
 * Finnish rolling stock series code to NeTEx TrainElementType mapping.
 * Based on Finnish series code system (fi.wikipedia.org/wiki/Sarjatunnus).
 * NeTEx enum: [buffetCar, carriage, engine, carTransporter, sleeperCarriage,
 * luggageVan, restaurantCarriage, other]
 */
class FinnishRollingStock {
    private FinnishRollingStock() {
    }

    static String mapWagonElementType(final String wagonType) {
        if (wagonType == null)
            return "carriage";
        return switch (wagonType) {
            // Restaurant/buffet cars (R = ravintolavaunu)
            case "ERd", "Rx", "Rk", "Rkt", "Rbkt", "Rbnqss" -> "restaurantCarriage";
            // Sleeping cars (m = makuuvaunu)
            case "Edm", "CEmt", "CEm", "Em" -> "sleeperCarriage";
            // Car transporters (G = tavaravaunut for cars)
            case "Gfot", "Gd", "Gb", "Gbl", "Gbln", "Ggk" -> "carTransporter";
            // Luggage/mail vans (F = konduktööri/matkatavara, P = posti)
            case "Fo", "Fots", "Fey", "Foek", "Fh", "F", "Po", "Pot" -> "luggageVan";
            // Generator/power cars, service vehicles, non-passenger rolling stock
            case "De", "Nom", "A", "M", "MT", "TT", "TTC", "Ttv",
                    "BXT", "BT", "BH", "BHpy", "BG", "BXG", "BXE",
                    "Hdk", "Hkb", "Hkba", "Hbr",
                    "Kas", "Tk3", "Tka7", "Tve1", "Uad", "Mas", "Gloss",
                    "CM", "CMH", "IM1", "IM2" ->
                "other";
            // All passenger carriages (E/C prefix, Sm/Dm EMU/DMU cars, Ex IC single-deck)
            default -> "carriage";
        };
    }

    static String mapFareClass(final String wagonType) {
        if (wagonType == null)
            return null;
        if (wagonType.startsWith("C"))
            return "firstClass";
        if (wagonType.startsWith("E"))
            return "standardClass";
        return null;
    }

    static String mapLocoPowerType(final String locomotiveType) {
        if (locomotiveType == null)
            return "other";
        if (locomotiveType.startsWith("S"))
            return "electricity";
        if (locomotiveType.startsWith("D"))
            return "diesel";
        return "other";
    }
}

/**
 * Generates a NeTEx Nordic compositions package containing train composition
 * and accessibility data per departure date.
 * Served as a separate ZIP (netex-nordic-compositions.zip).
 */
@Service
public class NeTExCompositionService {

    private static final Logger log = LoggerFactory.getLogger(NeTExCompositionService.class);
    private static final String COMPOSITIONS_FILENAME = "netex-nordic-compositions.zip";

    private final CompositionRepository compositionRepository;
    private final GeneratedExportRepository generatedExportRepository;
    private final NeTExIdGenerator idGenerator;
    private final NeTExCompositionWritingService compositionWritingService;

    public NeTExCompositionService(final CompositionRepository compositionRepository,
            final GeneratedExportRepository generatedExportRepository,
            final NeTExIdGenerator idGenerator,
            final NeTExCompositionWritingService compositionWritingService) {
        this.compositionRepository = compositionRepository;
        this.generatedExportRepository = generatedExportRepository;
        this.idGenerator = idGenerator;
        this.compositionWritingService = compositionWritingService;
    }

    @Transactional
    public void generateCompositions() {
        log.info("method=generateCompositions starting");
        final long startTime = System.currentTimeMillis();

        try {
            final LocalDate today = DateProvider.dateInHelsinki();
            final LocalDate start = today.minusDays(1);
            final LocalDate end = today.plusDays(30);

            final List<Composition> allCompositions = fetchCompositionsForRange(start, end);

            log.info("method=generateCompositions fetched {} compositions for range {} to {}",
                    allCompositions.size(), start, end);

            if (allCompositions.isEmpty()) {
                log.warn("method=generateCompositions no compositions found");
                return;
            }

            final byte[] zip = buildCompositionsZip(allCompositions);

            final GeneratedExport export = new GeneratedExport();
            export.data = zip;
            export.created = ZonedDateTime.now();
            export.fileName = COMPOSITIONS_FILENAME;
            generatedExportRepository.persist(List.of(export));

            final long durationMs = System.currentTimeMillis() - startTime;
            log.info("method=generateCompositions persisted ZIP, size={} bytes, compositions={}, durationMs={}",
                    zip.length, allCompositions.size(), durationMs);
        } catch (final Exception e) {
            final long durationMs = System.currentTimeMillis() - startTime;
            log.error("method=generateCompositions failed, durationMs={}", durationMs, e);
            throw new RuntimeException("NeTEx compositions generation failed", e);
        }
    }

    private List<Composition> fetchCompositionsForRange(final LocalDate start, final LocalDate end) {
        final List<Composition> all = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            all.addAll(compositionRepository.findByDepartureDateBetweenOrderByTrainNumber(date));
        }
        return all;
    }

    /**
     * Builds the compositions NeTEx ZIP from the given composition data.
     * Visible for testing.
     */
    public byte[] buildCompositionsZip(final List<Composition> compositions) {
        final Set<String> vehicleTypeIds = new LinkedHashSet<>();
        final List<NeTExVehicleType> vehicleTypes = buildVehicleTypes(compositions, vehicleTypeIds);
        final List<NeTExDatedVehicleJourney> datedJourneys = buildDatedVehicleJourneys(compositions, vehicleTypeIds);

        return compositionWritingService.writeZip(vehicleTypes, datedJourneys, ZonedDateTime.now());
    }

    private List<NeTExVehicleType> buildVehicleTypes(final List<Composition> compositions,
            final Set<String> vehicleTypeIds) {
        final Map<String, NeTExVehicleType> typeMap = new LinkedHashMap<>();

        for (final Composition composition : compositions) {
            for (final JourneySection section : composition.journeySections) {
                for (final Locomotive loco : section.locomotives) {
                    final String typeId = idGenerator.vehicleTypeId(loco.locomotiveType);
                    if (!typeMap.containsKey(typeId)) {
                        typeMap.put(typeId, new NeTExVehicleType(
                                typeId,
                                loco.locomotiveType,
                                FinnishRollingStock.mapLocoPowerType(loco.locomotiveType),
                                true,
                                0,
                                false,
                                false));
                    }
                }
                for (final Wagon wagon : section.wagons) {
                    final String typeId = idGenerator.vehicleTypeId(wagon.wagonType);
                    if (!typeMap.containsKey(typeId)) {
                        final boolean hasDisabled = Boolean.TRUE.equals(wagon.disabled);
                        typeMap.put(typeId, new NeTExVehicleType(
                                typeId,
                                wagon.wagonType,
                                null,
                                FinnishRollingStock.mapWagonElementType(wagon.wagonType).equals("motorCar"),
                                wagon.length,
                                hasDisabled,
                                Boolean.TRUE.equals(wagon.catering)));
                    }
                }
            }
        }

        vehicleTypeIds.addAll(typeMap.keySet());
        return new ArrayList<>(typeMap.values());
    }

    private List<NeTExDatedVehicleJourney> buildDatedVehicleJourneys(
            final List<Composition> compositions,
            final Set<String> vehicleTypeIds) {
        final List<NeTExDatedVehicleJourney> journeys = new ArrayList<>();

        for (final Composition composition : compositions) {
            final long trainNumber = composition.id.trainNumber;
            final LocalDate date = composition.id.departureDate;

            for (final JourneySection section : composition.journeySections) {
                final boolean hasWheelchair = section.wagons.stream()
                        .anyMatch(w -> Boolean.TRUE.equals(w.disabled));
                final boolean hasCatering = section.wagons.stream()
                        .anyMatch(w -> Boolean.TRUE.equals(w.catering));

                final List<NeTExTrainComponent> components = new ArrayList<>();
                int order = 1;

                for (final Locomotive loco : section.locomotives) {
                    components.add(new NeTExTrainComponent(
                            order++,
                            "engine",
                            idGenerator.vehicleTypeId(loco.locomotiveType),
                            loco.locomotiveType,
                            0,
                            false,
                            false,
                            null));
                }

                for (final Wagon wagon : section.wagons) {
                    components.add(new NeTExTrainComponent(
                            order++,
                            FinnishRollingStock.mapWagonElementType(wagon.wagonType),
                            idGenerator.vehicleTypeId(wagon.wagonType),
                            wagon.wagonType + " #" + wagon.salesNumber,
                            wagon.length,
                            Boolean.TRUE.equals(wagon.disabled),
                            Boolean.TRUE.equals(wagon.catering),
                            FinnishRollingStock.mapFareClass(wagon.wagonType)));
                }

                final String beginStation = section.beginTimeTableRow != null
                        ? section.beginTimeTableRow.station.stationShortCode
                        : null;
                final String endStation = section.endTimeTableRow != null
                        ? section.endTimeTableRow.station.stationShortCode
                        : null;

                final String journeyId = idGenerator.datedVehicleJourneyId(trainNumber, date, beginStation);

                journeys.add(new NeTExDatedVehicleJourney(
                        journeyId,
                        trainNumber,
                        date,
                        beginStation,
                        endStation,
                        section.totalLength,
                        section.maximumSpeed,
                        hasWheelchair,
                        hasCatering,
                        components));
            }
        }

        return journeys;
    }

    // --- DTOs ---

    public record NeTExVehicleType(
            String id,
            String name,
            String fuelType,
            boolean selfPropelled,
            int lengthCm,
            boolean wheelchair,
            boolean catering) {
    }

    public record NeTExTrainComponent(
            int order,
            String elementType,
            String vehicleTypeRef,
            String label,
            int lengthCm,
            boolean wheelchair,
            boolean catering,
            String fareClass) {
    }

    public record NeTExDatedVehicleJourney(
            String id,
            long trainNumber,
            LocalDate date,
            String beginStation,
            String endStation,
            int totalLength,
            int maximumSpeed,
            boolean hasWheelchair,
            boolean hasCatering,
            List<NeTExTrainComponent> components) {
    }
}
