package fi.livi.rata.avoindata.common.domain.gtfs;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/// Projection over the `train_location` table (populated from the PALA API's live GPS/track-circuit
/// positioning feed), joined with the train's next unresolved commercial stop from `time_table_row`.
///
/// This is a *separate* data source from the timetable-based "live trains" data served by
/// `LiveTrainController#getLiveTrainsByVersion` (which queries `live_time_table_train`/`Train`, i.e.
/// actual/predicted/scheduled stop times only — no GPS position). `GTFSTrainLocation` is consumed only by
/// the GTFS-Realtime `VehiclePosition` feed (`GTFSRealtimeService`/`FeedMessageService`) and by SIRI-VM
/// (`SiriVmGenerationService`/`VmJourneyConverter`); it is not used anywhere in the `Train`/`LiveTimeTableTrain`
/// timetable pipeline.
///
/// See `AvoinDataCommon/.../dao/gtfs/GTFSTrainRepository#getTrainLocations` for the native query producing
/// these rows.
public interface GTFSTrainLocation {
    /// Primary key of the source `train_location` row (one row per received GPS/position update).
    long getId();

    /// Departure date of the train, part of its composite identity together with {@link #getTrainNumber()}.
    LocalDate getDepartureDate();

    long getTrainNumber();

    /// Timestamp when this position was recorded (as reported by the upstream PALA feed).
    ZonedDateTime getTimestamp();

    /// Longitude (EPSG:4326 / WGS84), extracted from the stored `location` point (`st_x`).
    double getX();

    /// Latitude (EPSG:4326 / WGS84), extracted from the stored `location` point (`st_y`).
    double getY();

    ///  this is km/h
    int getSpeed();

    /// Estimated positional accuracy in meters, as reported by the upstream source.
    int getAccuracy();

    /// Station short code of the upcoming (not yet reached) commercial stop, resolved via
    /// `time_table_row` — the same stop used for {@link #getDelaySeconds()}. `null` when no upcoming stop
    /// could be resolved (e.g. the train has no more commercial stops left, or all remaining stops are
    /// cancelled/non-commercial).
    String getStationShortCode();

    /// Planned/commercial track of the upcoming stop identified by {@link #getStationShortCode()}. `null`
    /// under the same conditions as {@link #getStationShortCode()}, or when the track is not yet known.
    String getCommercialTrack();

    /// Whether the upcoming stop's track (see {@link #getCommercialTrack()}) is not yet confirmed/known.
    Boolean getUnknownTrack();

    /// Real-time delay, in seconds, against the upcoming commercial stop's scheduled time (positive = late,
    /// negative = early). {@code null} when no upcoming stop was resolved (see
    /// {@code GTFSTrainRepository#getTrainLocations}) or its live estimate is unknown.
    Integer getDelaySeconds();

    /// Whether the train is currently dwelling at (or, for the very first stop, not yet departed from) the
    /// resolved stop identified by {@link #getStationShortCode()} — as opposed to still approaching it.
    /// {@code null} when no upcoming/current stop was resolved. See
    /// {@code GTFSTrainRepository#getTrainLocations} for the derivation from `time_table_row.type`.
    Boolean getVehicleAtStop();
}
