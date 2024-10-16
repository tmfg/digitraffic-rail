package fi.livi.rata.avoindata.updater.service.gtfs.realtime;

import com.google.transit.realtime.GtfsRealtime;
import fi.livi.rata.avoindata.common.dao.gtfs.GTFSTripRepository;
import fi.livi.rata.avoindata.common.domain.gtfs.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSService.FIRST_STOP_SEQUENCE;
import static fi.livi.rata.avoindata.updater.service.gtfs.GTFSTripService.TRIP_REPLACEMENT;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

@Service
public class FeedMessageService {
    private static final Logger log = LoggerFactory.getLogger(FeedMessageService.class);
    public static final int PAST_LIMIT_MINUTES = 30;

    private final GTFSTripRepository gtfsTripRepository;

    public FeedMessageService(final GTFSTripRepository gtfsTripRepository) {
        this.gtfsTripRepository = gtfsTripRepository;
    }

    private static GtfsRealtime.FeedMessage.Builder createBuilderWithHeader() {
        return GtfsRealtime.FeedMessage.newBuilder()
                .setHeader(GtfsRealtime.FeedHeader.newBuilder()
                        .setGtfsRealtimeVersion("2.0")
                        .setTimestamp(new Date().getTime() / 1000)
                        .build());
    }

    public GtfsRealtime.FeedMessage createVehicleLocationFeedMessage(final List<GTFSTrainLocation> locations) {
        log.info("creating VehiclePositionFeedMessages for {} locations", locations.size());

        final TripFinder tripFinder = new TripFinder(gtfsTripRepository.findAll());

        final GtfsRealtime.FeedMessage message = createBuilderWithHeader()
                .addAllEntity(createVLEntities(tripFinder, locations))
                .build();

        log.info("created VehiclePositionFeedMessages for {} entities", message.getEntityCount());

        return message;
    }

    private static String createVesselLocationId(final GTFSTrainLocation location) {
        return String.format("%d_location_%d", location.getTrainNumber(), location.getId());
    }

    private static String createCancellationId(final GTFSTrain train) {
        return String.format("%d_cancel_%s", train.id.trainNumber, train.id.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    private static String createTripUpdateId(final GTFSTrain train) {
        return String.format("%d_update_%d", train.id.trainNumber, train.version);
    }

    private static String createStopId(final GTFSTimeTableRow row) {
        return GTFSTrainLocation.createStopId(row.stationShortCode, row.commercialTrack);
    }

    private List<GtfsRealtime.FeedEntity> createVLEntities(final TripFinder tripFinder, final List<GTFSTrainLocation> locations) {
        return locations.stream().map(location -> createVLEntity(tripFinder, location))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private GtfsRealtime.FeedEntity createVLEntity(final TripFinder tripFinder, final GTFSTrainLocation location) {
        final GTFSTrip trip = tripFinder.find(location);

        if(trip == null) {
            return null;
        }

        final String stopId = GTFSTrainLocation.createStopId(location.getStationShortCode(), location.getCommercialTrack());
        final String id = createVesselLocationId(location);
        final GtfsRealtime.FeedEntity.Builder builder = GtfsRealtime.FeedEntity.newBuilder();
        final GtfsRealtime.VehiclePosition.Builder vpBuilder = GtfsRealtime.VehiclePosition.newBuilder();

        if(stopId != null) {
            vpBuilder.setStopId(stopId);
        }

        vpBuilder.setTrip(GtfsRealtime.TripDescriptor.newBuilder()
                        .setRouteId(trip.routeId)
                        .setTripId(trip.tripId)
                        .setStartDate(location.getDepartureDate().format(DateTimeFormatter.BASIC_ISO_DATE))
                        .build())
                .setPosition(GtfsRealtime.Position.newBuilder()
                        .setLatitude((float)location.getY())
                        .setLongitude((float)location.getX())
                        .setSpeed(location.getSpeed())
                        .build())
                .setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
                        .setId(trip.id.trainNumber.toString())
                        .build());

        return builder
                .setId(id)
                .setVehicle(vpBuilder.build())
                .build();
    }

    private GtfsRealtime.FeedEntity createTUCancelledEntity(final GTFSTrip trip, final GTFSTrain train) {
        final GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder()
                        .setRouteId(trip.routeId)
                        .setTripId(trip.tripId)
                        .setStartDate(train.id.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                        .setScheduleRelationship(GtfsRealtime.TripDescriptor.ScheduleRelationship.CANCELED)
                        .build())
                .build();

        return GtfsRealtime.FeedEntity.newBuilder()
                .setId(createCancellationId(train))
                .setTripUpdate(tripUpdate)
                .build();
    }

    private boolean isInThePast(final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure) {
        final ZonedDateTime limit = ZonedDateTime.now().minusMinutes(PAST_LIMIT_MINUTES);

        final boolean isArrivalInPast = arrival == null || isBefore(arrival, limit);
        final boolean isDepartureInPast = departure == null || isBefore(departure, limit);

        return isArrivalInPast && isDepartureInPast;
    }

    private boolean isBefore(final GTFSTimeTableRow row, final ZonedDateTime limit) {
        // both scheduled time and live-estimate must be in the past to be skipped
        return row.liveEstimateTime != null && row.liveEstimateTime.isBefore(limit) && row.scheduledTime.isBefore(limit);
    }

    private GtfsRealtime.TripUpdate.StopTimeUpdate.Builder createStop(final int stopSequence, final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure) {
        final String stopId = createStopId(arrival == null ? departure : arrival);
        return GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                .setStopSequence(stopSequence)
                .setStopId(stopId);
    }

    private boolean isCancelled(final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure) {
        if(departure != null) {
            return departure.cancelled;
        }

        // check cancellation on arrival only when there is no departure
        return arrival != null && arrival.cancelled;
    }
    private GtfsRealtime.TripUpdate.StopTimeUpdate createStopTimeUpdate(final int stopSequence, final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure) {
        // setting cancelled stops as SKIPPED
        if(isCancelled(arrival, departure)) {
            return createStop(stopSequence, arrival, departure)
                    .setScheduleRelationship(GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED)
                    .build();
        }

        // it's in the past(PAST_LIMIT_MINUTES), don't report it!
        if(isInThePast(arrival, departure)) {
            return null;
        }

        final boolean arrivalHasTime = arrival != null && arrival.hasEstimateOrActualTime();
        final boolean departureHasTime = departure != null && departure.hasEstimateOrActualTime();

        // if there's no estimates yet, do not report
        if(!arrivalHasTime && !departureHasTime) {
            return null;
        }

        final GtfsRealtime.TripUpdate.StopTimeUpdate.Builder builder = createStop(stopSequence, arrival, departure);

        // GTFS delay is seconds, our difference is minutes
        if(arrivalHasTime) {
            builder.setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay(arrival.delayInSeconds())
                    .build());
        }
        if(departureHasTime) {
            // sometimes departure has live estimate that is before arrival's scheduled time(and arrival has no estimate or actual time)
            // in that case, fake a delay for arrival that's equals to departure's delay
            if(arrival != null) {
                if (!arrivalHasTime && arrival.scheduledTime.isAfter(departure.getActualOrEstimate())) {
                    builder.setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                            .setDelay(departure.delayInSeconds())
                            .build());
                }
                // sometimes departure might have estimate or actual that is before the arrival's actual or estimate
                // in that case, fake arrival to match departure
                if(arrivalHasTime && arrival.getActualOrEstimate().isAfter(departure.getActualOrEstimate())) {
                    builder.setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                            .setDelay(departure.delayInSeconds())
                            .build());
                }
            }

            builder.setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                    .setDelay(departure.delayInSeconds())
                    .build());
        }

        return builder.build();
    }

    private boolean delaysDiffer(final GtfsRealtime.TripUpdate.StopTimeUpdate previous, @Nonnull final GtfsRealtime.TripUpdate.StopTimeUpdate current) {
        if (previous == null) {
            return true;
        }

        final int previousDelay = previous.getDeparture().getDelay();

        if (current.hasArrival() && current.getArrival().getDelay() != previousDelay) {
            return true;
        }

        return current.hasDeparture() && current.getDeparture().getDelay() != previousDelay;
    }

    private List<GtfsRealtime.TripUpdate.StopTimeUpdate> createStopTimeUpdates(final GTFSTrain train) {
        final List<GtfsRealtime.TripUpdate.StopTimeUpdate> updates = new ArrayList<>();
        final List<GTFSTimeTableRow> activeRows = train.timeTableRows;
        int stopSequence = FIRST_STOP_SEQUENCE;

        if(activeRows.isEmpty()) {
            return updates; // return empty list, yes
        }

        // this is then previous stop that was added to updates-list
        GtfsRealtime.TripUpdate.StopTimeUpdate previous = createStopTimeUpdate(stopSequence++, null, activeRows.get(0));
        if(previous != null) {
            // if first stop and delay is negative, then
            // we generate a new stop with fabricated arrival that has the same delay as the departure
            // this is done because stop_times.txt has arrival and departure times for each stop, even for the first and last
            if(previous.getDeparture().getDelay() < 0) {
                final GtfsRealtime.TripUpdate.StopTimeUpdate.Builder builder = GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
                        .setStopId(previous.getStopId())
                        .setStopSequence(previous.getStopSequence());

                builder.setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                        .setDelay(previous.getDeparture().getDelay())
                        .build());

                builder.setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                        .setDelay(previous.getDeparture().getDelay())
                        .build());

                previous = builder.build();
            }

            updates.add(previous);
        }

        for(int i = 1; i < activeRows.size();) {
            final GTFSTimeTableRow arrival = activeRows.get(i++);
            final GTFSTimeTableRow departure = activeRows.size() == i ? null : activeRows.get(i++);

            // skip stations where train does not stop
            if(includeStop(arrival, departure)) {
                final GtfsRealtime.TripUpdate.StopTimeUpdate current = createStopTimeUpdate(stopSequence++, arrival, departure);

                // always include cancelled rows
                if (current != null && (delaysDiffer(previous, current) || current.getScheduleRelationship() == GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED)) {
                    updates.add(current);

                    previous = current;
                }
            }
        }

        return updates;
    }

    private boolean includeStop(final GTFSTimeTableRow arrival, final GTFSTimeTableRow departure) {

        // include only stops where scheduled times are different and stop is commercial(either arrival or departure)
        return departure == null || (!arrival.scheduledTime.equals(departure.scheduledTime) &&
                (isTrue(arrival.commercialStop) || isTrue(departure.commercialStop)));
    }

    private GtfsRealtime.FeedEntity createTUUpdateEntity(final GTFSTrip trip, final GTFSTrain train) {
        final List<GtfsRealtime.TripUpdate.StopTimeUpdate> stopTimeUpdates = createStopTimeUpdates(train);

        if(stopTimeUpdates.isEmpty()) {
            return null;
        }

        final GtfsRealtime.TripUpdate tripUpdate = GtfsRealtime.TripUpdate.newBuilder()
                .setTrip(GtfsRealtime.TripDescriptor.newBuilder()
                        .setRouteId(trip.routeId)
                        .setTripId(trip.tripId)
                        .setStartDate(train.id.departureDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                        .build())
                .addAllStopTimeUpdate(stopTimeUpdates)
                .build();

        return GtfsRealtime.FeedEntity.newBuilder()
                .setId(createTripUpdateId(train))
                .setTripUpdate(tripUpdate)
                .build();
    }

    public GtfsRealtime.FeedEntity createTUEntity(final TripFinder tripFinder, final GTFSTrain train) {
        try {
            final GTFSTrip trip = tripFinder.find(train);

            if (trip != null) {
                if (train.cancelled) {
                    return createTUCancelledEntity(trip, train);
                }

                if (trip.version != train.version) {
                    return createTUUpdateEntity(trip, train);
                }
            }
        } catch (final Exception e) {
            log.error("exception when creating entity " + train.id, e);
        }

        // new train, we do nothing.  the realtime-spec does not support creation of new trips!
        return null;
    }

    public List<GtfsRealtime.FeedEntity> createTUEntities(final TripFinder tripFinder, final List<GTFSTrain> trains) {
        return trains.stream()
                .map(train -> createTUEntity(tripFinder, train))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public GtfsRealtime.FeedMessage createTripUpdateFeedMessage(final List<GTFSTrain> trains) {
        log.info("creating TripUpdateFeedMessages trainCount={}", trains.size());

        final TripFinder tripFinder = new TripFinder(gtfsTripRepository.findAll());

        log.info("creating TripUpdateFeedMessages trainNumberCount={}", tripFinder.tripMap.entrySet().size());

        final GtfsRealtime.FeedMessage message = createBuilderWithHeader()
                .addAllEntity(createTUEntities(tripFinder, trains))
                .build();

        log.info("created TripUpdateFeedMessages entityCount={}", message.getEntityCount());

        return message;
    }

    static class TripFinder {
        private final Map<Long, List<GTFSTrip>> tripMap = new HashMap<>();

        TripFinder(final List<GTFSTrip> trips) {
            trips.forEach(t -> {
                tripMap.putIfAbsent(t.id.trainNumber, new ArrayList<>());
                tripMap.get(t.id.trainNumber).add(t);
            });
        }

        Stream<GTFSTrip> safeStream(final List<GTFSTrip> trips) {
            return trips == null ? Stream.empty() : trips.stream();
        }

        GTFSTrip find(final GTFSTrain train) {
            final List<GTFSTrip> trips = tripMap.get(train.id.trainNumber);

            return findTripFromList(trips, train.id.trainNumber, train.id.departureDate);
        }

        GTFSTrip find(final GTFSTrainLocation location) {
            final List<GTFSTrip> trips = tripMap.get(location.getTrainNumber());

            return findTripFromList(trips, location.getTrainNumber(), location.getDepartureDate());
        }

        GTFSTrip findTripFromList(final List<GTFSTrip> trips, final Long trainNumber, final LocalDate departureDate) {
            final List<GTFSTrip> filtered = safeStream(trips)
                    .filter(t -> t.id.trainNumber.equals(trainNumber))
                    .filter(t -> !t.id.startDate.isAfter(departureDate))
                    .filter(t -> !t.id.endDate.isBefore(departureDate))
                    .collect(Collectors.toList());

            if(filtered.isEmpty()) {
                log.trace("Could not find trip for train number " + trainNumber);
                return null;
            }

            if(filtered.size() > 1) {
                final Optional<GTFSTrip> replacement = findReplacement(filtered);

                if(replacement.isEmpty()) {
                    log.info("Multiple trips:" + filtered);
                    log.error("Could not find replacement from multiple " + trainNumber);
                }

                return replacement.orElse(null);
            }

            return filtered.get(0);
        }

        Optional<GTFSTrip> findReplacement(final List<GTFSTrip> trips) {
            return trips.stream()
                    .filter(trip -> trip.tripId.endsWith(TRIP_REPLACEMENT))
                    .findFirst();
        }
    }
}
