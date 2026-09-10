# SIRI-VM (Vehicle Monitoring) Implementation Plan

## Background: SIRI and Nordic SIRI Profile

**SIRI** (Service Interface for Real Time Information) is a European standard (CEN/TS 15531) for delivering real-time public transport information: predicted arrival/departure times, cancellations, extra services, platform changes, vehicle locations, disturbance notices, and more. It is the real-time counterpart to NeTEx, with which we publish planned rail transport timetables. SIRI describes short-term changes to the plan during an operating day; NeTEx describes the plan itself.

SIRI is a comprehensive standard with ten functional services. The Nordic SIRI Profile (published by Entur) restricts SIRI to three services:

- **SIRI-ET** — Estimated Timetable: continuous updates to planned routes
- **SIRI-VM** — Vehicle Monitoring: real-time GPS location of each vehicle
- **SIRI-SX** — Situation Exchange: text-based disturbance and passenger information

### Common Rules (Nordic Profile)

The Nordic profile also defines cross-cutting rules governing all services:

- **One file per delivery**: Complete dataset delivered as a single XML document
- **Local time**: All dates and times are in local (Helsinki) time, minimum second precision (e.g., 2026-07-10T21:22:23)
- **National stop identifiers**: Each stop reference must use the official identifier from the national stop register. For rail, this means PETI FSR:Quay identifier (specific platform). Same identifiers used by our NeTEx timetable
- **Tied to plan**: Each real-time data point references identifiers published in NeTEx
- **Producer codespace**: Both the producer reference (ProducerRef) and data source reference (DataSource) use Fintraffic Rail's registered codespace, FTR

### Relation to GTFS Data

Digitraffic already publishes some of this real-time data in GTFS-Realtime format. GTFS-Realtime has multiple message types (FeedEntity), and their SIRI service equivalents are:

- SIRI-ET corresponds to GTFS-Realtime TripUpdate
- SIRI-VM corresponds to GTFS-Realtime VehiclePosition
- SIRI-SX corresponds to GTFS-Realtime Alert

## Problem Statement

Implement SIRI-VM (Vehicle Monitoring) real-time data feed for Digitraffic Rail, following the Nordic SIRI profile specification. This is similar to the existing SIRI-ET (Estimated Timetable) implementation but provides vehicle position and status information.

## Architecture Overview

The implementation will follow the existing SIRI-ET pattern:

1. A service layer (`SiriVmGenerationService` / `SiriVmService`) generates XML exports from train data
2. Data is persisted in `GeneratedExport` table with filename `siri-vm.xml`
3. `SiriController` serves the XML via HTTP endpoint `/api/v1/siri/vm`
4. Feature flag `avoindataserver.siri.vm.enabled` controls endpoint availability

### Why an interpreter → domain IR (`VmActivity`) → marshaller pipeline, instead of one method

Both SIRI-ET and SIRI-VM split "decide what the real-time data says" from "express it as SIRI XML" via a small
immutable domain record (`VmActivity` for VM, the ET equivalents for ET) sitting between an *Interpreter* and a
*Marshaller*. This is not required by the SIRI spec — a single method could walk `GTFSTrainLocation` straight
into JAXB objects — but it was chosen deliberately, for the same reasons in both services:

- **Separation of concerns**: the interpreter decides journey/stop resolution and unit conversions; the
  marshaller only knows how to render already-resolved data as XML (JAXB types, timezones, `Duration`/`BigDecimal`
  formatting). Neither class needs to understand the other's job.
- **Testability without XML**: interpreter unit tests assert on plain record fields (e.g. "unresolved stop →
  `monitoredCallStopRef == null`") instead of parsing/walking a JAXB tree or running schema validation.
- **Stats need a pre-XML checkpoint**: `SiriVmService` computes `SiriVmStats` (locations received vs. activities
  emitted, i.e. how many were dropped as unresolvable) from the size of the interpreted list, before any XML is
  built. Without an intermediate list this would have to be a side-effecting counter threaded through XML
  construction.
- **Isolates JAXB/schema-version churn**: the interpreter has zero dependency on `uk.org.siri.siri21`; if the
  SIRI schema version changes, only the marshaller is affected.
- **Makes "drop unresolvable" a type-level contract**: the interpreter returns `Optional<VmActivity>` (empty for
  a location whose train has no published journey), so the "every real-time item must tie back to the plan"
  Nordic-profile rule can't be silently skipped by a future caller.

This is documented in the `VmActivity` Javadoc; see also `VmJourneyInterpreter`/`VmJourneyMarshaller`/
`SiriVmService` class Javadocs for the concrete split.

## Implementation Approach

### Phase 1: Analysis & Planning
- [x] Understand SIRI-VM specification from docs (PUBLIC-SIRI-VM-070926-124123.pdf)
- [x] Review existing SIRI-ET implementation pattern
- [x] Map train/vehicle data to SIRI-VM XML schema
- [x] Define required data transformations
- [x] Read Handbook N801 ("Håndbok N801 — Nasjonale rutedata") for cross-cutting requirements. Confirmed: SIRI-VM
  is expected to carry both position **and** real-time delay (§5.1); no other VM-specific field requirements
  found (N801 is the Norwegian national rutedata framework document — mostly submission-side/organizational
  context, not literally binding on Digitraffic Rail, but used here as the canonical description of what the
  Nordic SIRI profile expects of a VM producer). Result: added `Delay` (see Data Mapping below).

### Phase 2: Core Infrastructure ✅
- [x] Create `SiriVmService` class (XML generation logic)
- [x] Create `SiriVmGenerationService` class (data transformation)
- [x] Add `SiriVmUpdatingService` for periodic generation (`updater.siri.vm.enabled`, `@Scheduled`)
- [x] Add configuration flag `avoindataserver.siri.vm.enabled`

### Phase 3: Controller & Endpoint ✅
- [x] Add `SiriVmController.getSiriVm()` — a **separate controller** from `SiriController` (not a method on
      it), since `@ConditionalOnProperty` only gates whole bean registration, not individual `@RequestMapping`
      methods. This keeps SIRI-ET and SIRI-VM independently toggleable as required.
- [x] Map endpoint `/api/v1/siri/vm` returning XML
- [x] Add proper caching headers and response metadata (same convention as SIRI-ET: 30s cache, 5min freshness)
- [x] Implement feature flag gating with `@ConditionalOnProperty(name = "avoindataserver.siri.vm.enabled")`

### Phase 4: Testing & Integration ✅
- [x] Create controller integration test (`SiriVmControllerIntegrationTest`, mirrors `SiriControllerIntegrationTest`)
- [x] Create service-level unit tests (`SiriVmServiceTest`: schema validity, unresolved-journey skip, optional
      MonitoredCall)
- [x] Create golden XML test (`SiriVmGoldenXmlTest`, scenarios: `minimum`, `with-monitored-call`)
- [ ] End-to-end DB integration test (like `SiriEtDbIntegrationTest`) — not yet added; the unit-level tests above
      cover the mapping logic. Consider adding if/when this is exercised against real data.
- [ ] Verify periodic generation and updates against a real/staging environment

### Phase 5: Documentation
- [ ] Update API documentation (Swagger/OpenAPI)
- [ ] Document SIRI-VM response format
- [ ] Update configuration documentation

## Key Files to Create/Modify

### New Files (AvoinDataUpdater)

- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/SiriVmService.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/SiriVmGenerationService.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/SiriVmScheduledService.java` (or similar)
- `src/test/java/fi/livi/rata/avoindata/updater/service/siri/vm/...` (tests)

### Modified Files

- `AvoinDataServer/src/main/java/fi/livi/rata/avoindata/server/controller/api/SiriController.java`
  - Add `getSiriVm()` method
  - Add constant for VM filename
  
- `AvoinDataUpdater/src/main/resources/application.properties`
  - Add `avoindataserver.siri.vm.enabled=true` (default)

### Test Resources

- `src/test/resources/siri/expected-siri-vm-*.xml` (golden XML samples)

## SIRI-VM Data Mapping

Field-by-field status against the Entur SIRI-VM wiki spec and the checked-in golden XMLs. The wiki page and its examples define what the profile allows; our golden XMLs and marshaller show what this implementation emits. The XSD is only a secondary guardrail for syntax and types.

| XML path | Cardinality | Status | Notes |
|---|---|---|---|
| `VehicleMonitoringDelivery` | 1:1 | ✅ Implemented | Root delivery container. |
| &nbsp;&nbsp;↳ ResponseTimestamp | 1:1 | ✅ Implemented | When the dataset was published. |
| &nbsp;&nbsp;↳ VehicleActivity | 1:1 | ✅ Implemented | Live vehicle snapshot. |
| &nbsp;&nbsp;&nbsp;&nbsp;↳ RecordedAtTime | 1:1 | ✅ Implemented | Recorded timestamp. |
| &nbsp;&nbsp;&nbsp;&nbsp;↳ ValidUntilTime | 1:1 | ✅ Implemented | +5 min freshness window. |
| &nbsp;&nbsp;&nbsp;&nbsp;↳ MonitoredVehicleJourney | 1:1 | ✅ Implemented | Real-time journey payload. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ LineRef | 1:1 | ✅ Implemented | |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DirectionRef | 0:1 | ❌ Not implemented | Optional; omitted. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ FramedVehicleJourneyRef | 0:1 | ✅ Implemented | Framed form used. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleMode | 0:1 | ✅ Implemented | Fixed `rail`. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ OperatorRef | 0:1 | ✅ Implemented | |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ OriginRef / OriginName | 0:1 each | ✅ Implemented | First commercial stop. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DestinationRef / DestinationName | 0:1 each | ✅ Implemented | Last commercial stop. Not listed in the wiki Location section because it is journey-level, but the schema/model allow it on MonitoredVehicleJourney. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Monitored | 0:1 | ✅ Implemented | Fixed `true`. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DataSource | 1:1 | ✅ Implemented | Codespace. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleLocation | 1:1 | ✅ Implemented | WGS84 / EPSG:4326. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Longitude / Latitude | 1:1 | ✅ Implemented | |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Bearing | 0:1 | ❌ Not implemented | No heading data. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Velocity | 0:1 | ✅ Implemented | Ground speed. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Occupancy | 0:1 | ❌ Not implemented | No telemetry. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Delay | 1:1 | ✅ Implemented | Always emitted; `PT0S` if no delay. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ InCongestion | 0:1 | ❌ Not implemented | No source. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleStatus | 0:1 | ❌ Not implemented | No source. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ ProgressBetweenStops | 0:1 | ❌ Not implemented | No reliable source. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleJourneyRef | 0:1 | ➖ N/A | Framed ref used instead. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleRef | 1:1 | ✅ Implemented | Train number. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ MonitoredCall | 0:1 | ✅ Implemented | Current/next stop only. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ StopPointRef | 1:1 | ✅ Implemented | Required. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ StopPointName | 0:1 | ✅ Implemented | Readable label. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleAtStop | 0:1 | ✅ Implemented | Derived from row type. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleLocationAtStop | 0:1 | ✅ Implemented | Emitted when at stop. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DestinationDisplay | 0:1 | ✅ Implemented | Destination label from `ResolvedJourney.destination()` / published NeTEx. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ IsCompleteStopSequence | 1:1 | ✅ Implemented | Always `false`. |

Everything marked ❌ was checked against the live `train_location`/NeTEx data actually available to the updater; none has a ready data source today. If a future data source appears (e.g. a heading sensor, congestion feed, or dwell-detection logic), revisit this table rather than assuming the omission is permanent.

### `ProgressBetweenStops` investigation

`ProgressBetweenStops` (`LinkDistance` + `Percentage`, how far along the link between the previous and next stop the vehicle currently is) was investigated in detail on explicit request, to see whether the upstream PALA train-location feed could supply the missing distance-along-track data. Conclusion: **not with data available today**; deferred, not abandoned.

**What PALA actually offers.** The PALA API (`bruno/pala.json`, `/0.2/yksikot.json`) returns, per unit, a `sijainti` (`SijaintiDto`) object. Besides the `koordinaatti` (EPSG:3067) that `PalaYksikkoDeserializer` already reads, it optionally carries `ratakmsijainti` (`RatakmetaisyysData`: `ratanumero` line number, `ratakm` kilometre, `etaisyys` metres-within-km) — a genuine linear ("track-km") position along the physical rail line, which in principle tracks curvature far more accurately than a straight-line GPS-based estimate. `PalaYksikkoDeserializer` currently ignores this field entirely, and no locally checked-in PALA sample response includes it (`ratakmsijainti` is optional, not in `SijaintiDto`'s `required` list), so **its real-world prevalence in production PALA responses is unverified** — this would need confirming against a live/recent PALA payload (or with the PALA/Fintraffic team) before it could be relied on.

**What computing the field would additionally require.** Even with a reliable `ratakmsijainti` for the vehicle's *current* position, `LinkDistance`/`Percentage` also need the *previous* and *next* stop's positions expressed in the same track-km coordinate system, so a percentage-along-link can be computed directly (`(currentKm - prevStopKm) / (nextStopKm - prevStopKm)`). The codebase was searched for such a station↔track-km reference and none exists for this purpose:

- `TrackSection`/`TrackRange`/`TrackLocation` (`AvoinDataCommon/.../domain/tracksection/`), fed by `TrackSectionUpdater`/`TrackSectionDeserializer`, do store station + named track + start/end track-km ranges — but this dataset exists solely to let RUMA (track-work notice) consumers resolve *local yard trackage* ranges; it is exposed as-is via the public `/metadata/track-sections` endpoint and is never used internally to convert a track-km position into a station or vice versa. Its coverage of the full network (vs. just work-order-relevant yards) is unconfirmed.
- `RumaUtils.ratakmvaliToString()` only formats a track-km range as a human-readable string (e.g. `(002) 34+0940 > 35+0120`); it performs no coordinate/station resolution.
- `TrainRunningMessage` (track-circuit occupation events: `station`/`nextStation`/`previousStation`, `trackSection`/`nextTrackSection`/`previousTrackSection`) is a related but separate real-time feed telling us when a train enters/exits a track section — useful for discrete "train has passed station X" events, but it carries no distance/percentage information either.
- No other rail-network linear-reference API (station→ratakm master data) exists in this repository's data sources (`bruno/` collection contains only the PALA spec).

**Two paths forward, neither implemented:**
1. **Track-linear (accurate)** — capture PALA's `ratakmsijainti` into `TrainLocation`, and separately obtain/verify a station→track-km reference covering the whole network (a genuinely new upstream data source, not something reachable by combining what we already have). Higher effort and currently blocked on data availability we can't confirm.
2. **Geometric/haversine approximation (lower effort, lower accuracy)** — use the vehicle's already-available WGS84 position plus the previous/next stop's PETI-quay coordinates (the "previous stop" is not currently resolved anywhere in the VM pipeline; only the "upcoming" stop is) and estimate percentage via straight-line projection. Rejected for now: it ignores track curvature/tunnels, could yield out-of-range percentages (e.g. >100% or negative on curved track), and risks publishing a misleading value in a public feed for effectively an approximation with no real track-topology backing.

**Decision**: left as ❌ not implemented until either (a) PALA's `ratakmsijainti` availability is confirmed reliable in production, or (b) a genuine station↔track-km reference dataset becomes available. Revisit this table entry if either becomes true.


## Configuration Options

```properties
# Enable/disable SIRI-VM endpoint
avoindataserver.siri.vm.enabled=true

# Cache control (consider making this configurable like ET)
# Default: 30 seconds (same as ET)
```

## Implementation Notes

1. **Data Source**: Likely sourced from existing train journey/position data in the database
2. **Refresh Rate**: Probably ~1 minute updates (following same pattern as ET)
3. **XML Format**: SIRI 2.0 standard (matches ET implementation)
4. **Performance**: Cache at HTTP level (~30 sec) to reduce load
5. **Error Handling**: Missing data should not crash generation (similar to ET approach)
6. **Testing Strategy**: Use golden XML files with known scenarios (delays, occupancy, position data)

## Dependencies & Tools

- Spring Framework (already in use)
- JAXB for XML generation (already used by ET)
- JUnit + Mockito for tests (existing test framework)
- **digitraffic-common-java**: Leverage existing common services from `/lib/digitraffic-common-java` (subtree)
  - Check for utilities, base classes, and common domain objects
  - Reuse where applicable instead of reimplementing

## Code Quality & Best Practices

This implementation follows general Java best practices:

- **Clean Code**: Readable variable names, small focused methods, single responsibility principle
- **SOLID Principles**: Especially dependency injection and interface segregation
- **Testing**: Unit tests with mocks, integration tests with real database, golden XML tests
- **Error Handling**: Graceful degradation, proper exception handling
- **Documentation**: JavaDoc for public APIs, clear comments for complex logic
- **Performance**: Caching strategy, efficient SQL queries, minimal object creation in hot paths
- **Consistency**: Follow existing SIRI-ET patterns and project conventions

## Success Criteria

- [ ] SIRI-VM endpoint serves valid XML when `avoindataserver.siri.vm.enabled=true`
- [ ] Endpoint returns 404 when flag is disabled
- [ ] XML validates against SIRI-VM schema
- [ ] Cache headers are properly set
- [ ] Data is refreshed periodically
- [ ] Integration tests pass
- [ ] Golden XML tests pass with realistic scenarios

## Key References

- SIRI-VM Specification: `docs/PUBLIC-SIRI-VM-070926-124123.pdf`
- SIRI General: `docs/PUBLIC-General information SIRI-070926-122346.pdf`
- SIRI Introduction: `docs/PUBLIC-Introduction-070926-122019.pdf`
- GitHub Examples: https://github.com/entur/profile-examples/tree/master/siri/vehicle-monitoring
- Outline Example: https://raw.githubusercontent.com/entur/profile-examples/master/siri/siri-vm-outline.xml
