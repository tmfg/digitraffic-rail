# SIRI-ET (Estimated Timetable)

SIRI-ET describes how the real-time situation differs from the planned timetable during the operating day: delays,
cancellations, partial cancellations and platform changes. It is the real-time counterpart of the published NeTEx
timetable. The overall SIRI overview is in [`SIRI.md`](SIRI.md).

Flags: `avoindataserver.siri.et.enabled` (server), `updater.siri.et.enabled` (updater),
`updater.siri.et.fixed-rate-ms` (default 60000). Endpoint: `GET /api/v1/siri/et`. Generated file: `siri-et.xml`.

Profile: Entur SIRI-ET, https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370392/SIRI-ET (SIRI 2.0 schema).

## Data source

- Live trains: `GTFSTrain` with its `GTFSTimeTableRow`s (the same passenger-train data that GTFS-Realtime
  `TripUpdate` uses).
- Plan: the published NeTEx journeys in the database (`NeTExPublishedJourney` and its tracks). NeTEx generation stores
  the references SIRI needs for every (train number, departure date): service journey id, line, operator, journey
  pattern and the planned platform of each stop. SIRI-ET reads them back, so the document always uses exactly the same
  identifiers as the published NeTEx. When some stops of a schedule are cancelled for a day (partial cancellation), the
  NeTEx package has a separate shortened journey without those stops for that day, and SIRI-ET refers to it.
- Stops: the PETI stop registry. A station and a platform are converted to a PETI `FSR:Quay` id
  (`SiriStopResolver`).

## How the document is made

The updater generates the whole document again on a fixed schedule (every 60 seconds). A run has these stages:

1. **Prepare.** Read the published journeys for the operating days, check the PETI snapshot, fetch the live trains of
   exactly those journeys.
2. **Build.** Each train is converted into a small journey record (`EtJourneyConverter`) and written as SIRI
   XML (`EtJourneyMarshaller`). The converter has no SIRI types, the marshaller has no conversion logic.
3. **Validate.** The XML is checked against the SIRI 2.0 schema. An invalid document is never stored.
4. **Persist.** The XML is saved as `siri-et.xml` and `SIRI_ET` is updated in `/api/v1/last-updated`.
5. **Log.** One `event=rail.siri.generation` line with `rail.siri.service=et` (counts, skip reasons, PETI match rate,
   NeTEx dataset age).

The server serves the latest file as `application/xml`. Every response has `x-is-fresh` (the file is less than 5
minutes old), `x-timestamp` (when it was generated) and the content length. Responses can be cached for 30 seconds.

### Operating day

SIRI-ET shows differences inside one operating day. The Nordic profile says the operating day may differ from the
calendar day, so we use two dates: yesterday and today (`OperatingDayWindow`, the same window NeTEx stores).

Yesterday is included because some trains run past midnight. A train from yesterday is kept only while it is still
running: it is dropped when its last commercial stop has an actual time, or when the expected time of that stop is
more than 15 minutes in the past (stale data). Today's trains are always kept.

### Dependencies

- **Published NeTEx journeys.** If they are missing, there are none for the operating days, or the newest of them is
  more than 30 hours old, the run fails and nothing is stored. The last good file stays available (and
  `x-is-fresh` turns false after 5 minutes). Unlike SX, there is no partial result.
- **PETI stops.** If the PETI snapshot is empty, the run fails in the same way.

## Rules for one train

For each live train the converter decides what is published:

1. **Tie it to the plan.** The train (number + departure date) must be in the published NeTEx journeys. This gives
   the service journey id, line, operator and journey pattern. A train that is not published (for example a
   non-passenger train) is skipped and counted as `unresolved_journey`. Such skips are normal.
2. **Stops in order.** The rows of a train are sorted by time and paired into stops: origin (departure only), middle
   stops (arrival and departure) and terminus (arrival only).
3. **Commercial stops only.** A stop is kept only if passengers can board or leave, using the same rule as the NeTEx
   timetable (`CommercialStopRule`), so the real-time and the planned stop sequences match.
4. **Platform for every stop.** Each kept stop is converted to its PETI `FSR:Quay`. The real-time platform is used;
   when it is unknown (`unknownTrack` or empty) the planned platform from NeTEx is used instead (the planned
   platform of the right visit, if a station is visited twice). If even one stop cannot be resolved, the whole
   journey is skipped, because the profile requires a complete stop sequence. The skip is counted as
   `unresolved_stop.no_stop` (no PETI stop place for the station) or `unresolved_stop.no_quay` (stop place found,
   no matching platform), and the run is logged as `partial`.
5. **Past or future.** The newest stop that has an actual time is found. That stop and every stop before it are
   `RecordedCall`s, the rest are `EstimatedCall`s. A past stop without an actual time of its own is a "missed" call:
   it stays a `RecordedCall` and carries the estimate (`Expected…Time`) instead of the actual time. Recorded calls
   are therefore always the first part of the sequence and estimated calls the last part.
6. **Status of each time.** The estimate (or actual) is compared with the planned time. A difference of less than 60
   seconds is `onTime`, 60 seconds or more late is `delayed`, 60 seconds or more early is `early`. A cancelled stop
   is `cancelled`. Status is written only on `EstimatedCall` (see "What is left out").
7. **Cancellations.** A cancelled train gets `Cancellation=true` on the journey. A single cancelled stop gets it on
   the call. At the border of a partial cancellation, the last served stop before the cancelled part has its
   departure marked `cancelled`.
8. **Uncertain estimate.** If the source marks the delay as unknown (`unknownDelay`, from RAMI), the upcoming stop
   gets `PredictionInaccurate=true`.
9. **Monitored.** The journey is `Monitored=true` when any stop has an estimate or an actual time.
10. **Departure never before arrival.** After an arrival, the stored departure estimate may briefly be older than
    the actual arrival. Such an estimate is published as the arrival time.
11. **Platform change.** When the planned platform is known, the real-time platform is known and the two differ, the
    upcoming call gets a `StopAssignment` (`AimedQuayRef` planned, `ExpectedQuayRef` real). It is placed on the
    arrival side of the call, or on the departure side if the call has no arrival (the origin).
12. **Origin and destination names.** The station names of the first and last commercial stop are added for
    readability.

`RecordedAtTime` of a journey is when its own data last changed: the newest `modified` of the train and its rows
(single rows change on their own, for example when RAMI flags an unknown delay or a platform), never later than the
generation time, and the generation time if no `modified` exists. The frame around the journeys (and the delivery)
keeps the generation time.

An empty document is allowed: if no train is left, a delivery without a frame is written, because an empty
`EstimatedJourneyVersionFrame` is not schema-valid.

## Field mapping

Status: ✅ in the document, ❌ not implemented (no data or not needed). "Fixed" means the same value every time.
"Derived" means calculated from the live data. Times are in Helsinki time with whole seconds.

### Envelope and delivery

| Field | Status | Value |
|---|---|---|
| `Siri/@version` | ✅ | Fixed `2.0`. The profile is based on the SIRI 2.0 schema and no 2.1 fields are sent |
| `ServiceDelivery/ResponseTimestamp` | ✅ | Generation time |
| `ServiceDelivery/ProducerRef` | ✅ | Fixed `FTR` |
| `EstimatedTimetableDelivery/@version` | ✅ | Fixed `2.0` |
| `EstimatedTimetableDelivery/ResponseTimestamp` | ✅ | Generation time |
| `EstimatedJourneyVersionFrame/RecordedAtTime` | ✅ | Generation time |

### Journey (`EstimatedVehicleJourney`)

| Field | Status | Value |
|---|---|---|
| `RecordedAtTime` | ✅ | When the data of this journey last changed (see above) |
| `LineRef` | ✅ | Line of the published journey, same as in NeTEx |
| `DirectionRef` | ✅ | Fixed `0`. The profile says to use zero when direction is not known or used |
| `FramedVehicleJourneyRef/DataFrameRef` | ✅ | Departure date of the train |
| `FramedVehicleJourneyRef/DatedVehicleJourneyRef` | ✅ | Service journey id of the published journey, same as in NeTEx |
| `JourneyPatternRef` | ✅ | Journey pattern of the published journey, same as in NeTEx. Left out if not stored |
| `DataSource` | ✅ | Fixed `FTR` |
| `VehicleMode` | ✅ | Fixed `rail` |
| `OperatorRef` | ✅ | Operator of the published journey, same as in NeTEx. Left out if not stored |
| `Cancellation` | ✅ | `true` when the whole train is cancelled, otherwise not written |
| `Monitored` | ✅ | Derived: any stop has an estimate or an actual time |
| `OriginName` | ✅ | Station name of the first commercial stop |
| `DestinationName` | ✅ | Station name of the last commercial stop |
| `IsCompleteStopSequence` | ✅ | Fixed `true` (the profile requires it) |
| `RecordedCalls` | ✅ | Stops already served (see below) |
| `EstimatedCalls` | ✅ | Upcoming stops (see below) |

### Served stop (`RecordedCall`)

| Field | Status | Value |
|---|---|---|
| `StopPointRef` | ✅ | PETI `FSR:Quay` of the stop |
| `Order` | ✅ | Running number in the stop sequence, from 1 |
| `StopPointName` | ✅ | Station name |
| `Cancellation` | ✅ | `true` when the stop is cancelled, otherwise not written |
| `AimedArrivalTime` | ✅ | Planned arrival. Not on the origin |
| `ActualArrivalTime` | ✅ | Actual arrival. Not on the origin |
| `ExpectedArrivalTime` | ✅ | Only as a fallback when the served stop has no actual arrival (`missed`) |
| `AimedDepartureTime` | ✅ | Planned departure. Not on the terminus |
| `ActualDepartureTime` | ✅ | Actual departure. Not on the terminus |
| `ExpectedDepartureTime` | ✅ | Only as a fallback when the served stop has no actual departure (`missed`) |
| `ArrivalStatus`, `DepartureStatus`, boarding activities, `StopAssignment` | ❌ | SIRI 2.1 fields on `RecordedCall`. The deviation shows in the difference of aimed and actual time |

### Upcoming stop (`EstimatedCall`)

| Field | Status | Value |
|---|---|---|
| `StopPointRef` | ✅ | PETI `FSR:Quay` of the stop |
| `Order` | ✅ | Running number in the stop sequence |
| `StopPointName` | ✅ | Station name |
| `Cancellation` | ✅ | `true` when the stop is cancelled, otherwise not written |
| `PredictionInaccurate` | ✅ | `true` when the source flags the delay as unknown (RAMI `unknownDelay`) |
| `RequestStop` | ✅ | Fixed `false`. Trains are not stopped on request |
| `AimedArrivalTime` | ✅ | Planned arrival. Not on the origin |
| `ExpectedArrivalTime` | ✅ | Estimated arrival |
| `ArrivalStatus` | ✅ | Derived: `onTime`, `delayed`, `early` or `cancelled` |
| `ArrivalBoardingActivity` | ✅ | `alighting`, or `noAlighting` when the stop is cancelled |
| `ArrivalStopAssignment` | ✅ | Platform change: planned and real `FSR:Quay`. Sent on the arrival side |
| `AimedDepartureTime` | ✅ | Planned departure. Not on the terminus |
| `ExpectedDepartureTime` | ✅ | Estimated departure |
| `DepartureStatus` | ✅ | Derived like `ArrivalStatus` |
| `DepartureBoardingActivity` | ✅ | `boarding`, or `noBoarding` when the stop is cancelled |
| `DepartureStopAssignment` | ✅ | Platform change on a call without arrival (the origin) |

## What is left out

| Field | Reason |
|---|---|
| `ExtraJourney`, `EstimatedVehicleJourneyCode`, `RouteRef`, `PublishedLineName`, `GroupOfLinesRef`, `ExternalLineRef`, `VehicleJourneyRef`, `ExtraCall` | Used only for extra or replacement journeys. There are none on the passenger side in Finland |
| `VehicleRef` | The real-time data has no unit id |
| `AdditionalVehicleJourneyRef` | No link to other affected journeys in the data |
| `BlockRef` | Internal in the specification, not published |
| `Occupancy` | No passenger data |
| `PublicContact`, `OperationsContact` | No journey-specific contacts |
| `DestinationDisplay` on `EstimatedCall` | No source for the destination text of the vehicle |
| `PredictionInaccurate` on the journey | The source does not tell whether the whole journey is uncertain; it is marked per stop |
| `SituationRef` | Would link a journey or stop to a SIRI-SX situation. SX does not link back to ET |
| `JourneyRelations`, formation, occupancy and capacity fields | SIRI 2.1 features that the profile has not finalised, no data |
| `StopAssignment` on `RecordedCall` | SIRI 2.1 feature, so not sent for stops already served |

## Observability

- `event=rail.siri.generation`, `rail.siri.service=et`, `outcome=success|partial|error`.
- `partial` when no journey was emitted although trains were received, or when any stop could not be linked to PETI
  (`unresolved_stop.no_stop`, `unresolved_stop.no_quay`).
- Counters: trains received, journeys emitted and cancelled, skip reasons (`unresolved_journey`, `unresolved_stop.*`,
  `completed_carryover`), calls total, recorded and estimated, stop refs resolved and unresolved, PETI match rate.
- NeTEx source: `rail.siri.journey_source.dataset_version`, `rail.siri.journey_source.age` and, on failure,
  `rail.siri.journey_source.unavailable_reason` (`MISSING`, `EMPTY`, `STALE`, `PETI_EMPTY`).

## Tests

- Golden XML (`SiriEtGoldenXmlTest`): fixed input, output compared with `src/test/resources/siri/expected-siri-et-*.xml`.
- Service, generation service and statistics tests (`SiriEtServiceTest`, `SiriEtGenerationServiceTest`, `SiriEtStatsTest`): schema validity, skip reasons, outcome.
- Database test (`SiriEtDbIntegrationTest`) and controller tests (`SiriEtControllerIntegrationTest` and the disabled-flag
  variant).

## References

- Nordic SIRI profile, general information: https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370373
- Nordic SIRI profile, SIRI-ET: https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370392/SIRI-ET
- Entur examples: https://github.com/entur/profile-norway-examples/tree/master/siri
