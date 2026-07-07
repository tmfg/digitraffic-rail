package fi.livi.rata.avoindata.updater.service.netex;

import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExDatedVehicleJourney;
import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExTrainComponent;
import fi.livi.rata.avoindata.updater.service.netex.NeTExCompositionService.NeTExVehicleType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes NeTEx Nordic compositions XML and ZIP.
 * Uses direct XML generation for simplicity and control.
 */
public class NeTExCompositionWritingService {

    private static final String NETEX_VERSION = "1.15:NO-NeTEx-networktimetable:1.5";

    public static byte[] writeZip(final List<NeTExVehicleType> vehicleTypes,
            final List<NeTExDatedVehicleJourney> datedJourneys,
            final ZonedDateTime timestamp) {
        try {
            final String xml = writeXml(vehicleTypes, datedJourneys, timestamp);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("FIN_rail_compositions.xml"));
                zos.write(xml.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (final IOException e) {
            throw new RuntimeException("Failed to write compositions ZIP", e);
        }
    }

    static String writeXml(final List<NeTExVehicleType> vehicleTypes,
            final List<NeTExDatedVehicleJourney> datedJourneys,
            final ZonedDateTime timestamp) {
        final StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<PublicationDelivery version=\"").append(NETEX_VERSION).append("\"");
        xml.append(" xmlns=\"http://www.netex.org.uk/netex\"");
        xml.append(" xmlns:ns2=\"http://www.opengis.net/gml/3.2\"");
        xml.append(" xmlns:ns3=\"http://www.siri.org.uk/siri\">\n");
        xml.append("    <PublicationTimestamp>").append(timestamp.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append("</PublicationTimestamp>\n");
        xml.append("    <ParticipantRef>DT</ParticipantRef>\n");
        xml.append("    <Description>Finland rail composition and accessibility data</Description>\n");
        xml.append("    <dataObjects>\n");

        writeResourceFrame(xml, vehicleTypes);
        writeTimetableFrame(xml, datedJourneys);

        xml.append("    </dataObjects>\n");
        xml.append("</PublicationDelivery>\n");
        return xml.toString();
    }

    private static void writeResourceFrame(final StringBuilder xml, final List<NeTExVehicleType> vehicleTypes) {
        xml.append("        <ResourceFrame version=\"1\" id=\"DT:ResourceFrame:compositions\">\n");
        xml.append("            <vehicleTypes>\n");

        for (final NeTExVehicleType vt : vehicleTypes) {
            xml.append("                <VehicleType version=\"1\" id=\"").append(escapeXml(vt.id())).append("\">\n");
            xml.append("                    <Name>").append(escapeXml(vt.name())).append("</Name>\n");
            if (vt.selfPropelled()) {
                xml.append("                    <SelfPropelled>true</SelfPropelled>\n");
            }
            if (vt.fuelType() != null) {
                xml.append("                    <TypeOfFuel>").append(mapFuelType(vt.fuelType()))
                        .append("</TypeOfFuel>\n");
            }
            if (vt.lengthCm() > 0) {
                xml.append("                    <Length>").append(vt.lengthCm() / 100.0).append("</Length>\n");
            }
            if (vt.wheelchair()) {
                xml.append("                    <LowFloor>true</LowFloor>\n");
                xml.append("                    <HasLiftOrRamp>true</HasLiftOrRamp>\n");
            }
            xml.append("                </VehicleType>\n");
        }

        xml.append("            </vehicleTypes>\n");
        xml.append("        </ResourceFrame>\n");
    }

    private static void writeTimetableFrame(final StringBuilder xml,
            final List<NeTExDatedVehicleJourney> datedJourneys) {
        xml.append("        <TimetableFrame version=\"1\" id=\"DT:TimetableFrame:compositions\">\n");
        xml.append("            <vehicleJourneys>\n");

        for (final NeTExDatedVehicleJourney dj : datedJourneys) {
            xml.append("                <DatedServiceJourney version=\"1\" id=\"")
                    .append(escapeXml(dj.id())).append("\">\n");
            xml.append("                    <DepartureDate>").append(dj.date()).append("</DepartureDate>\n");
            xml.append("                    <ServiceJourneyRef ref=\"DT:ServiceJourney:")
                    .append(dj.trainNumber()).append("\"/>\n");

            // Train composition
            xml.append("                    <Train version=\"1\" id=\"DT:Train:")
                    .append(dj.trainNumber()).append("-").append(dj.date());
            if (dj.beginStation() != null) {
                xml.append("-").append(dj.beginStation());
            }
            xml.append("\">\n");
            xml.append("                        <TrainSize>\n");
            xml.append("                            <NumberOfCars>").append(dj.components().size())
                    .append("</NumberOfCars>\n");
            xml.append("                        </TrainSize>\n");

            if (!dj.components().isEmpty()) {
                xml.append("                        <components>\n");
                for (final NeTExTrainComponent comp : dj.components()) {
                    xml.append("                            <TrainComponent version=\"1\" id=\"DT:Train:")
                            .append(dj.trainNumber()).append("-").append(dj.date()).append("-")
                            .append(comp.order()).append("\">\n");
                    xml.append("                                <Label>").append(escapeXml(comp.label()))
                            .append("</Label>\n");
                    xml.append("                                <TrainElement version=\"1\" id=\"DT:TrainElement:")
                            .append(dj.trainNumber()).append("-").append(dj.date()).append("-")
                            .append(comp.order()).append("\">\n");
                    xml.append("                                    <TrainElementType>")
                            .append(comp.elementType()).append("</TrainElementType>\n");
                    if (comp.wheelchair()) {
                        xml.append("                                    <PassengerCapacity version=\"1\" id=\"DT:PC:")
                                .append(dj.trainNumber()).append("-").append(dj.date()).append("-")
                                .append(comp.order()).append("\">\n");
                        xml.append("                                        <WheelchairPlaceCapacity>1</WheelchairPlaceCapacity>\n");
                        xml.append("                                    </PassengerCapacity>\n");
                    }
                    if (comp.catering()) {
                        xml.append("                                    <facilities>\n");
                        xml.append("                                        <ServiceFacilitySet version=\"1\" id=\"DT:SFS:")
                                .append(dj.trainNumber()).append("-").append(dj.date()).append("-")
                                .append(comp.order()).append("\">\n");
                        xml.append("                                            <CateringFacilityList>restaurant</CateringFacilityList>\n");
                        xml.append("                                        </ServiceFacilitySet>\n");
                        xml.append("                                    </facilities>\n");
                    }
                    xml.append("                                </TrainElement>\n");
                    xml.append("                            </TrainComponent>\n");
                }
                xml.append("                        </components>\n");
            }

            xml.append("                    </Train>\n");

            // Accessibility summary
            if (dj.hasWheelchair()) {
                xml.append("                    <AccessibilityAssessment version=\"1\" id=\"DT:AA:")
                        .append(dj.trainNumber()).append("-").append(dj.date()).append("\">\n");
                xml.append("                        <MobilityImpairedAccess>true</MobilityImpairedAccess>\n");
                xml.append("                    </AccessibilityAssessment>\n");
            }

            xml.append("                </DatedServiceJourney>\n");
        }

        xml.append("            </vehicleJourneys>\n");
        xml.append("        </TimetableFrame>\n");
    }

    private static String mapFuelType(final String powerType) {
        if (powerType == null) return "other";
        return switch (powerType.toLowerCase()) {
            case "electric", "s" -> "electricity";
            case "diesel", "d" -> "diesel";
            default -> "other";
        };
    }

    private static String escapeXml(final String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
