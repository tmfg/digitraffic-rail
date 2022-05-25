package fi.livi.rata.avoindata.updater.service.gtfs;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.Lists;
import com.google.transit.realtime.GtfsRealtime;

import fi.livi.rata.avoindata.common.dao.gtfs.GTFSRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.GTFS;
import fi.livi.rata.avoindata.common.utils.DateProvider;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Calendar;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.CalendarDate;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.GTFSDto;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

@Service
public class GTFSWritingService {
    private Logger log = LoggerFactory.getLogger(this.getClass());

    @Value("${gtfs.dir:}")
    private String gtfsDir;

    @Autowired
    private GTFSRepository gtfsRepository;

    @Autowired
    private DateProvider dateProvider;

    @Transactional
    public List<File> writeGTFSFiles(final GTFSDto gtfsDto) throws IOException {
        return writeGTFSFiles(gtfsDto, "gtfs.zip");
    }

    private void persist(final byte[] data, final String fileName) {
        final GTFS gtfs = new GTFS();
        gtfs.data = data;
        gtfs.created = dateProvider.nowInHelsinki();
        gtfs.fileName = fileName;

        gtfsRepository.persist(Arrays.asList(gtfs));
    }

    @Transactional
    public List<File> writeGTFSFiles(GTFSDto gtfsDto, String zipFileName) throws IOException {
        log.info("Generating {}", zipFileName);

        final List<File> files = writeGtfsFiles(gtfsDto);
        writeGtfsZipFile(files, zipFileName);

        persist(Files.readAllBytes(new File(zipFileName).toPath()), zipFileName);

        return files;
    }

    @Transactional
    public void writeRealtimeGTFS(final GtfsRealtime.FeedMessage message, final String fileName) {
        log.info("Generating {} with {} entities", fileName, message.getEntityCount());

        persist(message.toByteArray(), fileName);
    }

    private List<File> writeGtfsFiles(final GTFSDto gtfsDto) {
        List<File> files = new ArrayList<>();

        files.add(
                write(getPath("agency.txt"), gtfsDto.agencies, "agency_id,agency_name,agency_url,agency_timezone,agency_phone,agency_lang",
                        agency -> String.format("%s,%s,%s,%s,%s,fi", agency.id, agency.name, agency.url, agency.timezone, agency.phoneNumber)));


        files.add(write(getPath("stops.txt"), gtfsDto.stops,
                "stop_id,stop_name,stop_desc,stop_lat,stop_lon,stop_url,location_type,parent_station,stop_code", stop ->
                        String.format("%s,%s,,%s,%s,,,,%s", stop.stopId, stop.name != null ? stop.name : stop.stopCode, stop.latitude,
                                stop.longitude, stop.source != null ? stop.source.shortCode : stop.stopCode)
        ));

        files.add(write(getPath("routes.txt"), gtfsDto.routes, "route_id,agency_id,route_short_name,route_long_name,route_desc,route_type",
                route -> String.format("%s,%s,%s,%s,,%s", route.routeId, route.agencyId, route.shortName, route.longName, route.type)));

        files.add(write(getPath("trips.txt"), gtfsDto.trips, "route_id,service_id,trip_id,trip_headsign,block_id,trip_short_name,shape_id",
                trip -> String.format("%s,%s,%s,%s,,%s,%s", trip.routeId, trip.serviceId, trip.tripId, trip.headsign, trip.shortName, trip.shapeId)));

        files.add(write(getPath("shapes.txt"), gtfsDto.shapes, "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence",
                shape -> String.format("%s,%s,%s,%s", shape.shapeId, shape.latitude, shape.longitude, shape.sequence)));

        List<StopTime> stopTimes = new ArrayList<>();
        List<Calendar> calendars = new ArrayList<>();
        Set<CalendarDate> calendarDates = new HashSet<>();
        for (final Trip trip : gtfsDto.trips) {
            stopTimes.addAll(trip.stopTimes);
            calendars.add(trip.calendar);
            calendarDates.addAll(trip.calendar.calendarDates);
        }

        files.add(write(getPath("stop_times.txt"), stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type", st -> String
                        .format("%s,%s,%s,%s,%s,%s,%s", st.tripId, format(st.arrivalTime), format(st.departureTime), st.track != null ? st.stopId + "_" + st.track : st.stopId,
                                st.stopSequence, st.pickupType, st.dropoffType)));


        files.add(write(getPath("calendar.txt"), calendars,
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date", c -> String
                        .format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", c.serviceId, formatBoolean(c.monday), formatBoolean(c.tuesday),
                                formatBoolean(c.wednesday), formatBoolean(c.thursday), formatBoolean(c.friday), formatBoolean(c.saturday),
                                formatBoolean(c.sunday), format(c.startDate), format(c.endDate))));

        files.add(write(getPath("calendar_dates.txt"), new ArrayList<>(calendarDates), "service_id,date,exception_type",
                cd -> String.format("%s,%s,%s", cd.serviceId, format(cd.date), cd.exceptionType)));

        final LocalDate minStartDate = gtfsDto.trips.stream().min(Comparator.comparing(left -> left.calendar.startDate))
                .get().calendar.startDate;
        final LocalDate maxEndDate = gtfsDto.trips.stream().max(Comparator.comparing(left -> left.calendar.endDate)).get().calendar.endDate;

        files.add(write(getPath("feed_info.txt"), Lists.newArrayList(1),
                "feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version", cd -> String
                        .format("%s,%s,%s,%s,%s,%s", "Fintraffic", "https://www.digitraffic.fi/rautatieliikenne/", "fi", format(minStartDate),
                                format(maxEndDate), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(
                                        ZoneId.of("Z")).format(dateProvider.nowInHelsinki()))));


        return files;
    }

    private void writeGtfsZipFile(final List<File> files, final String zipFileName) throws IOException {
        FileOutputStream fos = new FileOutputStream(zipFileName);
        ZipOutputStream zos = new ZipOutputStream(fos);

        for (final File file : files) {
            zos.putNextEntry(new ZipEntry(file.getName()));

            byte[] bytes = Files.readAllBytes(file.toPath());
            zos.write(bytes, 0, bytes.length);
        }

        zos.closeEntry();
        zos.close();
    }

    private String getPath(final String fileName) {
        return gtfsDir + fileName;
    }

    public static String format(LocalDate localDateTime) {
        if (localDateTime == null) {
            return "";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        return localDateTime.format(formatter);
    }

    public static String format(Duration duration) {
        if (duration == null) {
            return "";
        }

        long seconds = duration.getSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }

    public static String formatBoolean(Boolean object) {
        if (object == null) {
            return "";
        }

        return object ? "1" : "0";
    }

    public static boolean isValidTrack(final String trackNumber) {
        if (trackNumber == null) {
            return false;
        }
        final Pattern validTrack = Pattern.compile("[0-9]+");
        Matcher m = validTrack.matcher(trackNumber);
        return m.matches();
    }

    private String nullableToString(Object o) {
        if (o == null) {
            return "";
        } else {
            return o.toString();
        }
    }

    private <E> File write(String filename, List<E> entities, String header, Function<E, String> converter) {
        final File file = new File(filename);

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8)) {
            writer.write(header + "\n");

            for (final E entity : entities) {
                writer.write(converter.apply(entity) + "\n");
            }
        } catch (FileNotFoundException e1) {
            log.error("Error writing GTFS file", e1);
        } catch (IOException e1) {
            log.error("Error writing GTFS file", e1);
        }

        return file;
    }

}
