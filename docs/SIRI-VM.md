# SIRI-VM (Vehicle Monitoring)

SIRI-VM publishes the current position of each passenger train, together with its delay and the stop it is approaching
or standing at. It is the real-time counterpart of GTFS-Realtime `VehiclePosition`. The overall SIRI overview is in
[`SIRI.md`](SIRI.md).

Flags: `avoindataserver.siri.vm.enabled` (server), `updater.siri.vm.enabled` (updater),
`updater.siri.vm.fixed-rate-ms` (default 60000). Endpoint: `GET /api/v1/siri/vm`. Generated file: `siri-vm.xml`.
The flags are independent of the ET and SX flags.

Profile: Entur SIRI-VM, https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370425/SIRI-VM (SIRI 2.0 schema).
The profile (and Håndbok N801 §5.1) expects VM to carry both position and real-time delay.

## Data source

- Positions: `train_location` (GPS fix and speed), the same table GTFS-Realtime `VehiclePosition` uses. For every
  passenger train the newest position that is at most 30 minutes old is used (the same window as the GTFS-Realtime feed).
  Passenger trains are train numbers below 2000 and 8000–9999 (temporary rule, see DPO-2412).
- Next stop and delay: `GTFSTrainRepository.getTrainLocations`. One query adds to each position the train's current or
  upcoming commercial stop (station, platform, delay and the unknown-delay flag) and whether the train is at the stop.
- Plan: the published NeTEx journeys in the database (service journey id, line, operator, planned platform of each
  stop), the same as ET.
- Stops: the PETI stop registry (`FSR:Quay`), the same as ET.

The three data sources are resolved with the same classes as ET (`JourneyRefResolver`, `StationUicLookup`,
`StationNameLookup`, `SiriStopResolver`), so ET and VM always give the same line, journey and platform ids for a train.

## How the feed is made

The updater generates the whole document again every 60 seconds, in the same stages as ET:

1. **Prepare.** Read the published journeys for the operating days (yesterday and today), check the PETI snapshot,
   fetch the latest positions and the next-stop data of passenger trains.
2. **Build.** `VmJourneyConverter` turns each position into a small record (`VmActivity`); `VmJourneyMarshaller` writes
   it as SIRI XML. The converter has no SIRI types, the marshaller has no conversion logic.
3. **Validate.** The XML is checked against the SIRI 2.0 schema. An invalid document is never stored.
4. **Persist.** The XML is saved as `siri-vm.xml` and `SIRI_VM` is updated in `/api/v1/last-updated`.
5. **Log.** One `event=rail.siri.generation` line with `rail.siri.service=vm`.

The server serves the latest file as `application/xml`. Every response has `x-is-fresh` (less than 5 minutes old),
`x-timestamp` and the content length. Responses can be cached for 30 seconds.

### Dependencies

- **Published NeTEx journeys.** If they are missing, there are none for the operating days, or the newest of them is
  more than 30 hours old, the run fails and nothing is stored. The last good file stays available.
- **PETI stops.** If the PETI snapshot is empty, the run fails in the same way.

The profile requires that the timetable exists before positions are sent. This is enforced by the two points above and
by dropping a position whose train is not in the published journeys. PETI snapshot age is only logged, it is not a
reason to fail.

## Rules for one position

1. **One `VehicleActivity` per train**, from its newest position.
2. **Tie it to the plan.** The train (number + departure date) must be in the published NeTEx journeys. If not, the
   position is dropped. No made-up journey reference is published. The run is `partial` only if the whole feed is
   empty although positions were received.
3. **Next stop (`MonitoredCall`).** It is the stop the train is approaching or standing at, never the stop it has just
   left. The stop is the first commercial, non-cancelled stop with no actual time whose estimate is still in the
   future. If the train is at a station and both its arrival and departure are still open, the departure wins, because
   the train has not left yet.
4. **Platform of the next stop.** The real-time platform is used. When it is unknown (`unknownTrack`) or empty, the
   planned platform from NeTEx is used. A station that is visited twice by the same train is told apart by its visit
   number, which is calculated from the train's full stop list only in this fallback case (one batch query per run,
   not one per train). If the platform cannot be resolved to a PETI `FSR:Quay`, the `MonitoredCall` is left out and the
   vehicle is still published.
5. **Train at the terminus.** When a train has arrived at its last stop there is no upcoming stop. The arrived terminus
   is then used as the `MonitoredCall` (with `VehicleAtStop=true`), so the train does not suddenly lose its stop.
6. **`VehicleAtStop`.** True when the next stop is a departure and its arrival has happened or does not exist (the
   origin). False when the next row is the arrival, or when the arrival is still waiting. `VehicleLocationAtStop` is
   written only when `VehicleAtStop` is true, and it is the vehicle's own last GPS fix.
7. **Delay.** The estimate of the next stop minus its planned time, in whole seconds. If it is not known, `PT0S`
   (the field is mandatory in the profile and must not be left out).
8. **Uncertain delay (`InCongestion`).** When the source marks the delay as unknown (RAMI `unknownDelay`), the
   vehicle gets `InCongestion=true`. The profile has no field for "the estimate is not reliable"; this is the closest
   one ("other circumstances which may lead to further delays"). This is a decision, not a real congestion signal.
   `Delay` is still written as normal.
9. **Origin and destination.** The first and last stop of the published journey, with the planned platform (a train
   that is en route has no live position at its origin, and none yet at its destination). They are the first and last
   of the published tracks, which are ordered by `sequenceIndex` (the journey position; `visitIndex` is only a
   per-station counter and would misplace a station visited twice). `planned_track` is never null, and NeTEx
   generation fills the platform of every commercial stop in three steps (realised or upcoming track, history, the
   station's first PETI platform), so no stop is dropped and the endpoints do not shift. Only a station that PETI
   does not know, or that has no platforms in PETI, is published without a quay ref. That is a data-quality problem
   reported to the PETI maintainers.
10. **Speed.** Source speed is km/h and is converted to m/s, then rounded to a whole number as the schema requires.
11. **Validity.** `ValidUntilTime` is `RecordedAtTime` + 5 minutes. The recorded time is the time of the GPS fix, not
    the generation time.

## Field mapping

Status: ✅ in the feed, ❌ not implemented. Cardinality is from the profile (`1:1` required, `0:1` optional).
Everything marked ❌ was checked against the data available to the updater; none has a ready source today.

### Delivery

| Field | Cardinality | Status | Value |
|---|---|---|---|
| `Siri/@version` | | ✅ | Fixed `2.0` |
| `ServiceDelivery/ResponseTimestamp` | 1:1 | ✅ | Generation time |
| `ServiceDelivery/ProducerRef` | 1:1 | ✅ | Fixed `FTR` |
| `VehicleMonitoringDelivery/@version` | | ✅ | Fixed `2.0` |
| `VehicleMonitoringDelivery/ResponseTimestamp` | 1:1 | ✅ | Generation time |

### Vehicle (`VehicleActivity`)

| Field | Cardinality | Status | Value |
|---|---|---|---|
| `RecordedAtTime` | 1:1 | ✅ | Time of the GPS fix (`train_location.timestamp`), per train |
| `ValidUntilTime` | 1:1 | ✅ | Recorded time + 5 minutes |
| `MonitoredVehicleJourney/LineRef` | 1:1 | ✅ | Line of the published journey, same as NeTEx |
| `…/DirectionRef` | 0:1 | ❌ | Optional in VM (unlike ET), so left out instead of a meaningless fixed `0` |
| `…/FramedVehicleJourneyRef` | 0:1 | ✅ | `DataFrameRef` = departure date, `DatedVehicleJourneyRef` = service journey id, same as NeTEx |
| `…/VehicleJourneyRef` | 0:1 | ➖ | Not used, the framed form is used |
| `…/VehicleMode` | 0:1 | ✅ | Fixed `rail` |
| `…/OperatorRef` | 0:1 | ✅ | Operator of the published journey, same as NeTEx |
| `…/OriginRef`, `OriginName` | 0:1 | ✅ | First stop of the published journey: planned `FSR:Quay` and station name |
| `…/DestinationRef`, `DestinationName` | 0:1 | ✅ | Last stop of the published journey: planned `FSR:Quay` and station name |
| `…/Monitored` | 0:1 | ✅ | Fixed `true` (a position exists) |
| `…/DataSource` | 1:1 | ✅ | Fixed `FTR` |
| `…/VehicleLocation` (`Longitude`, `Latitude`) | 1:1 | ✅ | GPS fix, WGS84 (EPSG:4326) |
| `…/Bearing` | 0:1 | ❌ | No heading in `train_location`, only position and speed |
| `…/Velocity` | 0:1 | ✅ | Speed in whole m/s |
| `…/Occupancy` | 0:1 | ❌ | No passenger data |
| `…/Delay` | 1:1 | ✅ | Always written. `PT0S` if the delay is not known |
| `…/InCongestion` | 0:1 | ✅ | `true` only when the delay is flagged unknown (see rule 8), otherwise not written |
| `…/VehicleStatus` | 0:1 | ❌ | No operational status (breakdown, not in service) in the data |
| `…/ProgressBetweenStops` | 0:1 | ❌ | See "ProgressBetweenStops" below |
| `…/VehicleRef` | 1:1 | ✅ | Train number |
| `…/MonitoredCall` | 0:1 | ✅ | Next stop only, left out if its platform cannot be resolved |
| `…/MonitoredCall/StopPointRef` | 1:1 | ✅ | PETI `FSR:Quay` of the next stop |
| `…/MonitoredCall/StopPointName` | 0:1 | ✅ | Station name |
| `…/MonitoredCall/VehicleAtStop` | 0:1 | ✅ | See rule 6 |
| `…/MonitoredCall/VehicleLocationAtStop` | 0:1 | ✅ | GPS fix, only when at the stop |
| `…/MonitoredCall/DestinationDisplay` | 0:1 | ✅ | Name of the last stop of the journey |
| `…/IsCompleteStopSequence` | 1:1 | ✅ | Always `false`, since only one call is reported |

### ProgressBetweenStops

`ProgressBetweenStops` (`LinkDistance` and `Percentage`, how far the vehicle is between the previous and next stop) was
investigated and left out.

- The PALA position feed can carry a track-kilometre position (`ratakmsijainti`: track number, kilometre and metres). It
  is not read today, and no sample response has it, so its real availability in production is not known.
- Even with it, the track-kilometre positions of the previous and next stop are needed. No such station reference exists
  in our data. `TrackSection` data is only for yard tracks in the track work notices and is not used to convert
  positions. `TrainRunningMessage` tells when a train passes a station but has no distance.
- A straight-line estimate from the GPS fix and the quay coordinates was rejected. It ignores curves and tunnels, can give
  values below 0 or above 100 %, and would put a misleading number in a public feed. The previous stop is also not
  resolved anywhere in the VM pipeline.

Revisit if PALA's `ratakmsijainti` is confirmed reliable in production, or a station to track-kilometre reference
appears.

## Unknown platform: fallback to the planned platform

When the real-time platform of the next stop is unknown, the stop used to be treated as unresolved. For ET the whole
journey was dropped, for VM the `MonitoredCall` was dropped. That lost data for nothing: an unknown platform means the
confirmed platform is not known yet, not that it changed. Both feeds now use the planned platform from NeTEx
(`NeTExPublishedJourneyTrack`) instead.

### `visitIndex`

The planned platform is stored per journey, station and visit. `visitIndex` is the 0-based count of the visit to a
station among the commercial stops of the journey (the same key NeTEx generation stores). It is needed when a station is
visited more than once, for example a turn-back service that uses a different platform each time
(`PlannedTrackLookup.plannedTrack(trainNumber, departureDate, stationShortCode, visitIndex)`).

### ET

ET already goes through all rows of a train, so it counts the visits as it goes
(`resolveVisitCounts.merge(station, 1, Integer::sum) - 1`).

### VM: why it needs more

VM gets only one row per train: the next stop, chosen by the native query. The query leaves out stops already passed, so
there is no `visitIndex` available for free. Three options were considered:

1. **Calculate `visitIndex` in the SQL query.** Rejected. It would repeat the arrival and departure pairing and the
   commercial stop counting as window functions over the whole train history. Complex, error-prone and hard to test
   compared to the same Java code.
2. **Assume `visitIndex = 0`.** Rejected. Silently wrong for a station visited more than once.
3. **Fetch the train's rows on demand, only when the platform is unknown** (chosen). This is rare, so the extra cost is
   small. The rows are read through `TimeTableRowsLookup`, paired into commercial stops with `CommercialStopVisits.of`
   (which follows the ET pairing, so both feeds agree on what a commercial stop is), and
   `CommercialStopVisits.currentVisitIndex` gives the current visit.

The generation service reads the rows of all such trains in one batch query per run (`GTFSTrain.timeTableRows` is
eager, so a query per train would load the full row set of each of them in every cycle). A train that also needs the
terminus fallback (rule 5) is fetched only once. The same condition decides both whether a train needs the rows and
whether the converter uses the fallback (`VmJourneyConverter.needsPlannedTrackFallback`), so the two cannot drift
apart. A `null` or empty platform is treated as unknown too, because ingestion stores a missing platform as `""`.

`currentVisitIndex` has a subtlety ET does not need. A stop is still the current stop while the train waits at it
(arrival has an actual time, departure does not). So eligibility is checked per row (arrival and departure each), not
per stop, following the per-row filters of `GTFSTrainRepository.getTrainLocations`.

### Row order

`timeTableRows` has no `@OrderBy` and JPA does not guarantee an order. The pairing in `CommercialStopVisits.of` and
`EtJourneyConverter.pairRows` sorts by scheduled time. When several rows have exactly the same scheduled time (a
zero-dwell stop, or a station border with zero scheduled travel time), the order inside that group is rebuilt by
grouping the rows by station (`orderRows` / `reorderTiedGroup`). A pairwise comparator was rejected, because it broke
the `Comparator` contract for ties of three or more. A 90-day production check found no such tie. This is a defensive fix
and not a fix for an observed problem.

### Shared classes

- `siri/common/CommercialStopVisits`: pairs a train's rows into commercial stops and gives `currentVisitIndex`. Used by the
  VM fallback only.
- `siri/common/TimeTableRowsLookup`: `rowsFor(trainNumber, departureDate)`. Keeps `VmJourneyConverter` free of a database
  dependency; tests give a fake one.

## Observability

- `event=rail.siri.generation`, `rail.siri.service=vm`, `outcome=success|partial|error`.
- `partial` when positions were received but no activity was emitted.
- Counters: `rail.siri.locations.received` and `rail.siri.activities.emitted` (the difference is the positions dropped
  because their train has no published journey).
- NeTEx source: `rail.siri.journey_source.dataset_version`, `rail.siri.journey_source.age` and, on failure,
  `rail.siri.journey_source.unavailable_reason` (`MISSING`, `EMPTY`, `STALE`, `PETI_EMPTY`).

## Tests

- Golden XML (`SiriVmGoldenXmlTest`, scenarios `minimum`, `with-monitored-call`, `at-stop`): output compared with
  `src/test/resources/siri/expected-siri-vm-*.xml`.
- `VmJourneyConverterTest`, `SiriVmServiceTest`, `SiriVmGenerationServiceTest`: conversion rules, schema validity,
  dropped positions, optional fields.
- `SiriVmDbIntegrationTest`: NeTEx references, a real `train_location` row and its `time_table_row`s in the database;
  the next-stop query runs for real.
- Controller tests (`SiriVmControllerIntegrationTest` and the disabled-flag variant): headers, 404 cases.

## References

- Nordic SIRI profile, SIRI-VM: https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370425/SIRI-VM
- Entur examples: https://github.com/entur/profile-norway-examples/tree/master/siri/vehicle-monitoring
  (`siri-vm-before-stop.xml` and `siri-vm-at-stop.xml` show that `MonitoredCall` is the stop the vehicle is approaching
  or standing at)
