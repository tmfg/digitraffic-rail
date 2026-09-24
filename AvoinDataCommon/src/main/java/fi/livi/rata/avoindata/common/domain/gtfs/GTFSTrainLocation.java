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
    /// could be resolved — including once the train has actually arrived at its terminus and has no further
    /// stop to report: unlike the "next stop" lookup itself (shared, unchanged, with GTFS-Realtime), resolving
    /// an *already-arrived* terminus is only ever needed by SIRI-VM, so this query deliberately leaves it
    /// `null` here and SIRI-VM resolves it separately, in Java, from the train's full row list (see
    /// `CommercialStopVisits#resolveTerminusFallback`, wired in `SiriVmGenerationService` via
    /// `TerminusFallbackTrainLocation`) rather than reporting it through this field. Callers other than
    /// SIRI-VM (e.g. GTFS-Realtime) must not assume a `null` value here means the train has left the network —
    /// it may simply be dwelling at its terminus.
    String getStationShortCode();

    /// Planned/commercial track of the upcoming stop identified by {@link #getStationShortCode()}. `null`
    /// under the same conditions as {@link #getStationShortCode()}, or when the track is not yet known.
    String getCommercialTrack();

    /// Whether the upcoming stop's track (see {@link #getCommercialTrack()}) is not yet confirmed/known.
    Boolean getUnknownTrack();

    /// Whether the source system (RAMI) has flagged the upcoming stop's delay estimate (see
    /// {@link #getDelaySeconds()}) as unreliable — i.e. it genuinely does not know how long the train will
    /// have to wait. `null`/`false` in the normal case. Consumed by SIRI-VM's `InCongestion` (there is no
    /// dedicated "estimate is unreliable" field in the Nordic profile — see
    /// {@code GTFSTrainRepository#getTrainLocations} for why `InCongestion` was chosen); GTFS-Realtime does not
    /// currently consume this.
    Boolean getUnknownDelay();

    /// Real-time delay, in seconds, against the upcoming commercial stop's scheduled time (positive = late,
    /// negative = early). {@code null} when no upcoming stop was resolved (see
    /// {@code GTFSTrainRepository#getTrainLocations}) or its live estimate is unknown.
    Integer getDelaySeconds();

    /// Whether the train is currently dwelling at (or, for the very first stop, not yet departed from) the
    /// resolved stop identified by {@link #getStationShortCode()} — as opposed to still approaching it.
    /// {@code null} when no upcoming/current stop was resolved. See
    /// {@code GTFSTrainRepository#getTrainLocations} for the derivation from `time_table_row.type`.
    ///
    /// Backed by {@link #getVehicleAtStopValue()} rather than mapped directly: MySQL does not preserve the
    /// `TINYINT(1)`/boolean display-width metadata for a computed (`CASE`/comparison) column once it passes
    /// through the query's derived table, so the JDBC driver reports it as a plain `Integer` — projecting
    /// that straight to `Boolean` fails with `UnsupportedOperationException` at runtime (verified against a
    /// real MySQL instance, not just H2/mocks). Projecting the raw `Integer` and converting in Java sidesteps
    /// the issue entirely.
    default Boolean getVehicleAtStop() {
        final Integer value = getVehicleAtStopValue();
        return value == null ? null : value != 0;
    }

    /// Raw `0`/`1`/`null` value backing {@link #getVehicleAtStop()} — see its Javadoc for why this indirection
    /// is needed. Not intended to be called directly outside this interface.
    Integer getVehicleAtStopValue();
}
