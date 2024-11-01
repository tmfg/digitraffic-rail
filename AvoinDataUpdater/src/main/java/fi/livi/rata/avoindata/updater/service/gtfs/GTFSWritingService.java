package fi.livi.rata.avoindata.updater.service.gtfs;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSConstants.LOCATION_TYPE_STOP;

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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.springframework.scheduling.annotation.Scheduled;
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
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Platform;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.StopTime;
import fi.livi.rata.avoindata.updater.service.gtfs.entities.Trip;

@Service
public class GTFSWritingService {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

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

        gtfsRepository.persist(List.of(gtfs));
    }

    @Transactional
    public List<File> writeGTFSFiles(final GTFSDto gtfsDto, final String zipFileName) throws IOException {
        log.info("Generating {}", zipFileName);

        log.info("Creating files {}", zipFileName);
        final List<File> files = writeGtfsFiles(gtfsDto);
        log.info("Writing zip {}", zipFileName);
        writeGtfsZipFile(files, zipFileName);

        log.info("Persist {}", zipFileName);
        persist(Files.readAllBytes(new File(zipFileName).toPath()), zipFileName);

        return files;
    }

    @Scheduled(fixedDelay = 1000*60*60, initialDelay = 1000*60)
    @Transactional
    public void deleteOldZips() {
        final Integer numberOfDeletedRows =  gtfsRepository.deleteOldZips(ZonedDateTime.now().minusDays(14));
        log.info(String.format("Deleted %s zips", numberOfDeletedRows));
    }

    @Transactional
    public void writeRealtimeGTFS(final GtfsRealtime.FeedMessage message, final String fileName) {
        log.info("Generating {} with {} entities", fileName, message.getEntityCount());

        persist(message.toByteArray(), fileName);
    }

    private List<File> writeGtfsFiles(final GTFSDto gtfsDto) {
        final List<File> files = new ArrayList<>();

        files.add(
                write("agency.txt", gtfsDto.agencies, "agency_id,agency_name,agency_url,agency_timezone,agency_phone,agency_lang",
                        agency -> String.format("%s,%s,%s,%s,%s,fi", agency.id, agency.name, agency.url, agency.timezone, agency.phoneNumber)));

        files.add(write("stops.txt", gtfsDto.stops,
                "stop_id,stop_name,stop_desc,stop_lat,stop_lon,stop_url,location_type,parent_station,stop_code,platform_code", stop ->
                        String.format("%s,%s,%s,%s,%s,,%s,%s,,%s", stop.stopId, stop.name != null ? stop.name : stop.stopCode, stop.description != null ? stop.description : "",
                                stop.latitude, stop.longitude, stop.locationType, stop.locationType == LOCATION_TYPE_STOP && stop.source != null ? stop.source.shortCode : "", stop instanceof Platform ? ((Platform) stop).track : "")
        ));

        files.add(write("routes.txt", gtfsDto.routes, "route_id,agency_id,route_short_name,route_long_name,route_desc,route_type",
                route -> String.format("%s,%s,%s,%s,,%s", route.routeId, route.agencyId, route.shortName, route.longName, route.type)));

        files.add(write("trips.txt", gtfsDto.trips, "route_id,service_id,trip_id,trip_headsign,block_id,trip_short_name,shape_id,wheelchair_accessible,bikes_allowed",
                trip -> String.format("%s,%s,%s,%s,,%s,%s,%s,%s", trip.routeId, trip.serviceId, trip.tripId, trip.headsign, trip.shortName, trip.shapeId, trip.wheelchair, trip.bikesAllowed != null ? trip.bikesAllowed : "")));

        files.add(write("shapes.txt", gtfsDto.shapes, "shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence",
                shape -> String.format("%s,%s,%s,%s", shape.shapeId, shape.latitude, shape.longitude, shape.sequence)));

        final List<StopTime> stopTimes = new ArrayList<>();
        final List<Calendar> calendars = new ArrayList<>();
        final Set<CalendarDate> calendarDates = new HashSet<>();
        for (final Trip trip : gtfsDto.trips) {
            stopTimes.addAll(trip.stopTimes);
            calendars.add(trip.calendar);
            calendarDates.addAll(trip.calendar.calendarDates);
        }

        files.add(write("translations.txt", gtfsDto.translations, "table_name,field_name,field_value,language,translation",
                t -> String.format("stops,stop_name,%s,%s,%s", t.finnishName(), t.language(), t.translation())));

        files.add(write("stop_times.txt", stopTimes,
                "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type", st -> String
                        .format("%s,%s,%s,%s,%s,%s,%s", st.tripId, format(st.arrivalTime), format(st.departureTime), st.track != null ? st.stopId + "_" + st.track : st.stopId + "_0",
                                st.stopSequence, st.pickupType, st.dropoffType)));


        files.add(write("calendar.txt", calendars,
                "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date", c -> String
                        .format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", c.serviceId, formatBoolean(c.monday), formatBoolean(c.tuesday),
                                formatBoolean(c.wednesday), formatBoolean(c.thursday), formatBoolean(c.friday), formatBoolean(c.saturday),
                                formatBoolean(c.sunday), format(c.startDate), format(c.endDate))));

        files.add(write("calendar_dates.txt", new ArrayList<>(calendarDates), "service_id,date,exception_type",
                cd -> String.format("%s,%s,%s", cd.serviceId, format(cd.date), cd.exceptionType)));

        final LocalDate minStartDate = gtfsDto.trips.stream().min(Comparator.comparing(left -> left.calendar.startDate))
                .get().calendar.startDate;
        final LocalDate maxEndDate = gtfsDto.trips.stream().max(Comparator.comparing(left -> left.calendar.endDate)).get().calendar.endDate;

        files.add(write("feed_info.txt", Lists.newArrayList(1),
                "feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version", cd -> String
                        .format("%s,%s,%s,%s,%s,%s", "Fintraffic", "https://www.digitraffic.fi/rautatieliikenne/", "fi", format(minStartDate),
                                format(maxEndDate), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(
                                        ZoneId.of("Z")).format(dateProvider.nowInHelsinki()))));


        return files;
    }

    private void writeGtfsZipFile(final List<File> files, final String zipFileName) throws IOException {
        final FileOutputStream fos = new FileOutputStream(zipFileName);
        final ZipOutputStream zos = new ZipOutputStream(fos);

        for (final File file : files) {
            zos.putNextEntry(new ZipEntry(file.getName()));

            final byte[] bytes = Files.readAllBytes(file.toPath());
            zos.write(bytes, 0, bytes.length);
        }

        zos.closeEntry();
        zos.close();
    }

    private String getPath(final String fileName) {
        return gtfsDir + fileName;
    }

    public static String format(final LocalDate localDateTime) {
        if (localDateTime == null) {
            return "";
        }

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        return localDateTime.format(formatter);
    }

    public static String format(final Duration duration) {
        if (duration == null) {
            return "";
        }

        final long seconds = duration.getSeconds();
        final long absSeconds = Math.abs(seconds);
        final String positive = String.format("%d:%02d:%02d", absSeconds / 3600, (absSeconds % 3600) / 60, absSeconds % 60);

        return seconds < 0 ? "-" + positive : positive;
    }

    public static String formatBoolean(final Boolean object) {
        if (object == null) {
            return "";
        }

        return object ? "1" : "0";
    }

    private String nullableToString(final Object o) {
        if (o == null) {
            return "";
        } else {
            return o.toString();
        }
    }

    private <E> File write(final String filename, final List<E> entities, final String header, final Function<E, String> converter) {
        final File file = new File(getPath(filename));

        try (final OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(filename), StandardCharsets.UTF_8)) {
            writer.write(header + "\n");

            for (final E entity : entities) {
                writer.write(converter.apply(entity) + "\n");
            }
        } catch (final IOException e1) {
            log.error("Error writing GTFS file", e1);
        }

        return file;
    }

}
