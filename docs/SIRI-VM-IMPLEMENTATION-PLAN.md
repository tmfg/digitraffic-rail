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
- **National stop identifiers**: Each stop reference must use the official identifier from the national stop register. For rail, this means PETI FSR:Quay identifier (specific platform). Same identifiers used by our NeTEx timetable. These identifiers (`FSR:Quay:*`, `FSR:StopPlace:*`) are stable and do not change over time, so code can safely cache/rely on them across generation cycles without re-validating identity each run.
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
3. `SiriVmController` serves the XML via HTTP endpoint `/api/v1/siri/vm`
4. Feature flag `avoindataserver.siri.vm.enabled` controls endpoint availability

### Why an interpreter/converter → domain IR (`VmActivity`) → marshaller pipeline, instead of one method

Both SIRI-ET and SIRI-VM split "decide what the real-time data says" from "express it as SIRI XML" via a small
immutable domain record (`VmActivity` for VM, the ET equivalents for ET) sitting between an *Interpreter/Converter*
and a *Marshaller* (VM's class is named `VmJourneyConverter`; ET's is still `EtJourneyInterpreter` — see
"Follow-up: Consistency Improvements" below for the deferred rename). This is not required by the SIRI spec — a
single method could walk `GTFSTrainLocation` straight into JAXB objects — but it was chosen deliberately, for the
same reasons in both services:

- **Separation of concerns**: the interpreter/converter decides journey/stop resolution and unit conversions; the
  marshaller only knows how to render already-resolved data as XML (JAXB types, timezones, `Duration`/`BigDecimal`
  formatting). Neither class needs to understand the other's job.
- **Testability without XML**: interpreter/converter unit tests assert on plain record fields (e.g. "unresolved
  stop → `monitoredCallStopRef == null`") instead of parsing/walking a JAXB tree or running schema validation.
- **Stats need a pre-XML checkpoint**: `SiriVmService` computes `SiriVmStats` (locations received vs. activities
  emitted, i.e. how many were dropped as unresolvable) from the size of the converted list, before any XML is
  built. Without an intermediate list this would have to be a side-effecting counter threaded through XML
  construction.
- **Isolates JAXB/schema-version churn**: the interpreter/converter has zero dependency on `uk.org.siri.siri21`; if
  the SIRI schema version changes, only the marshaller is affected.
- **Makes "drop unresolvable" a type-level contract**: the interpreter/converter returns `Optional<VmActivity>`
  (empty for a location whose train has no published journey), so the "every real-time item must tie back to the
  plan" Nordic-profile rule can't be silently skipped by a future caller.

This is documented in the `VmActivity` Javadoc; see also `VmJourneyConverter`/`VmJourneyMarshaller`/
`SiriVmService` class Javadocs for the concrete split.

## Implementation Approach

### Phase 1: Analysis & Planning
- [x] Understand SIRI-VM specification from the Entur SIRI-VM wiki page (see `SIRI.md` → "Canonical source material")
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
- [x] Add `SiriVmController.getSiriVm()` — a **separate controller** from `SiriEtController` (not a method on
      it), since `@ConditionalOnProperty` only gates whole bean registration, not individual `@RequestMapping`
      methods. This keeps SIRI-ET and SIRI-VM independently toggleable as required.
- [x] Map endpoint `/api/v1/siri/vm` returning XML
- [x] Add proper caching headers and response metadata (same convention as SIRI-ET: 30s cache, 5min freshness)
- [x] Implement feature flag gating with `@ConditionalOnProperty(name = "avoindataserver.siri.vm.enabled")`

### Phase 4: Testing & Integration ✅
- [x] Create controller integration test (`SiriVmControllerIntegrationTest`, mirrors `SiriEtControllerIntegrationTest`)
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

## Key Files (as actually implemented)

### New Files (AvoinDataUpdater)

- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/SiriVmService.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/SiriVmGenerationService.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/VmJourneyConverter.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/VmJourneyMarshaller.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/SiriVmStats.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/vm/model/VmActivity.java`
- `src/main/java/fi/livi/rata/avoindata/updater/service/siri/SiriVmUpdatingService.java` (note: sibling to
  `SiriUpdatingService` in `service/siri/`, not nested under `service/siri/vm/` — that package holds only
  VM-specific converter/marshaller/service/model classes)
- `src/test/java/fi/livi/rata/avoindata/updater/service/siri/vm/...` (tests)

### New Files (AvoinDataServer)

- `src/main/java/fi/livi/rata/avoindata/server/controller/api/SiriVmController.java` — a **separate controller**
  from `SiriEtController`, not a method added to it (see Phase 3 above for why).
- `src/test/java/fi/livi/rata/avoindata/server/controller/api/SiriVmControllerIntegrationTest.java`

### Modified Files

- `AvoinDataServer/src/main/resources/application.properties` — added `avoindataserver.siri.vm.enabled=true`.
- `AvoinDataUpdater/src/main/resources/application.properties` — added `updater.siri.vm.enabled=true` and
  `updater.siri.vm.fixed-rate-ms`.
- `AvoinDataCommon/.../dao/gtfs/GTFSTrainRepository.java` — added `getTrainLocations(...)` (the native query VM's
  live position/delay/vehicle-at-stop data comes from) and the `unknown_delay` column.
- `AvoinDataCommon/.../domain/gtfs/GTFSTrainLocation.java` — added fields surfaced by the query above.
- `NeTExPublishedJourneyTrack` — added an explicit `@OrderBy("sequenceIndex ASC")` (needed for VM's
  `OriginRef`/`DestinationRef` first/last-stop resolution; see `SIRI.md` for why).

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
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DirectionRef | 0:1 | ❌ Not implemented | Deliberate scope choice, not a data gap: optional in VM (unlike ET), so omitted entirely rather than emit a meaningless fixed `"0"` placeholder. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ FramedVehicleJourneyRef | 0:1 | ✅ Implemented | Framed form used. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleMode | 0:1 | ✅ Implemented | Fixed `rail`. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ OperatorRef | 0:1 | ✅ Implemented | |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ OriginRef / OriginName | 0:1 each | ✅ Implemented | First commercial stop. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DestinationRef / DestinationName | 0:1 each | ✅ Implemented | Last commercial stop. Not listed in the wiki Location section because it is journey-level, but the schema/model allow it on MonitoredVehicleJourney. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Monitored | 0:1 | ✅ Implemented | Fixed `true`. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ DataSource | 1:1 | ✅ Implemented | Codespace. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleLocation | 1:1 | ✅ Implemented | WGS84 / EPSG:4326. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Longitude / Latitude | 1:1 | ✅ Implemented | |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Bearing | 0:1 | ❌ Not implemented | Genuine data gap: `train_location` (GPS fix) carries no heading/compass reading, only position and ground speed. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Velocity | 0:1 | ✅ Implemented | Ground speed. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Occupancy | 0:1 | ❌ Not implemented | Genuine data gap: no passenger-count/load telemetry exists anywhere in the source systems available to the updater. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ Delay | 1:1 | ✅ Implemented | Always emitted; `PT0S` if no delay. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ InCongestion | 0:1 | ✅ Implemented | Derived from `time_table_row.unknown_delay` (source system's own "can't reliably estimate the wait" flag) — accepted design decision, fitting the spec's "other circumstances which may lead to further delays" wording; does not affect `Delay`, which is always emitted regardless. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ VehicleStatus | 0:1 | ❌ Not implemented | Genuine data gap: no vehicle operational-status signal (e.g. breakdown/not-in-service) exists in `train_location`/GTFS data; only position, speed, and timetable-derived delay are available. |
| &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;↳ ProgressBetweenStops | 0:1 | ❌ Not implemented | Genuine data gap, investigated in depth (see "`ProgressBetweenStops` investigation" below): a candidate PALA field exists but its production reliability is unverified, and no station↔track-km reference dataset exists to pair it with. |
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
# Enable/disable SIRI-VM endpoint (server) / generation (updater)
avoindataserver.siri.vm.enabled=true
updater.siri.vm.enabled=true
updater.siri.vm.fixed-rate-ms=60000

# HTTP cache-control: max-age=30, public (CACHE_SECONDS constant in SiriVmController, matching SiriEtController -
# not externalized as a property, same as ET)
```

## Implementation Notes

1. **Data Source**: `GTFSTrainLocation` (via `GTFSTrainRepository.getTrainLocations`, backed by the same
   `train_location` table GTFS-Realtime `VehiclePosition` uses), joined to published NeTEx journeys.
2. **Refresh Rate**: `updater.siri.vm.fixed-rate-ms` (default 60000ms/1 minute), same pattern as ET.
3. **XML Format**: SIRI 2.0 standard (matches ET implementation).
4. **Performance**: HTTP-level `Cache-Control: max-age=30, public` (`SiriVmController.CACHE_SECONDS`), same as ET.
5. **Error Handling**: `PetiUnavailableException`/`PublishedJourneysUnavailableException` fail the generation
   cycle fast (logged, previous `GeneratedExport` left in place) rather than publishing partial/stale data.
6. **Testing Strategy**: golden XML files (`SiriVmGoldenXmlTest`) plus unit tests for the converter/marshaller/
   service layers and an integration test against a real database (`SiriVmServiceTest` and friends).

## Dependencies & Tools

- Spring Framework (already in use)
- JAXB for XML generation (already used by ET)
- JUnit + Mockito for tests (existing test framework)
- **digitraffic-common-java** (`lib/digitraffic-common-java` subtree): reused for common services, e.g. in
  `SiriVmGenerationService`.

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

- [x] SIRI-VM endpoint serves valid XML when `avoindataserver.siri.vm.enabled=true`
- [x] Endpoint returns 404 when flag is disabled (`@ConditionalOnProperty` on `SiriVmController`)
- [x] XML validates against SIRI-VM schema (`SiriWritingService` schema validation in the VALIDATE stage)
- [x] Cache headers are properly set (`Cache-Control: max-age=30, public`)
- [x] Data is refreshed periodically (`SiriVmUpdatingService`, default every 60s)
- [x] Integration tests pass
- [x] Golden XML tests pass with realistic scenarios

## Key References

- SIRI-VM Specification: https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370425/SIRI-VM (canonical, living source — see `SIRI.md` → "Canonical source material" for the full list of wiki pages used)
- GitHub Examples: https://github.com/entur/profile-norway-examples/tree/master/siri/vehicle-monitoring
- Outline Example: https://raw.githubusercontent.com/entur/profile-norway-examples/master/siri/siri-vm-outline.xml

## Follow-up: Consistency Improvements

### SIRI-ET Refactoring (Deferred)

VM code now uses the `*Converter` / `converter` naming pattern consistently with the `convertLocationsToActivities()` method. 
For consistency across the codebase, ET code should be refactored similarly:

- Rename `EtJourneyInterpreter` → `EtJourneyConverter`
- Rename field `interpreter` → `converter` in `SiriEtService`
- Rename/adjust method names from `interpret(...)` to `convert(...)` where appropriate

This is a cosmetic refactoring without functional impact and should be deferred to avoid unnecessary scope creep.
It does not affect any external APIs or test contracts.

### GTFSTrainLocation Boolean vs Primitive Types (Consideration)

Currently, `GTFSTrainLocation` uses `Boolean` (nullable) for two fields:

- **`getUnknownTrack()`** — Wrapped `Boolean`
  - Usages: `BooleanUtils.isTrue(location.getUnknownTrack())` everywhere (GTFS, VM, Udot)
  - Semantics: `null` and `false` are treated identically ("track is known")
  - **Could be:** `boolean` primitive without loss of meaning

- **`getVehicleAtStop()`** — Wrapped `Boolean`
  - Current semantics: `null` means "no upcoming stop resolved", `true`/`false` means known state
  - Database derivation: Ties directly to `time_table_row.type` presence/value
  - **Could be:** `boolean` (since `null` → `false` when no stop is resolved)

**Recommendation for future work:**
- Convert both `getUnknownTrack()` and `getVehicleAtStop()` to `boolean` primitives
- Update all call sites to remove `BooleanUtils.isTrue()` guards
- Verify SIRI XML marshalling correctly handles `false` values (vs. missing elements for `null`)

This is low-priority housekeeping; current `Boolean` usage works correctly but is more verbose than necessary.

### Origin/Destination endpoint derivation on published journeys (Follow-up)

The current VM journey-endpoint derivation still assumes the first and last stored published track represent the
journey's true origin/destination. That is only safe when the endpoint stops themselves have known tracks.

If a real endpoint stop has an unknown track, the published track list can skip it and the VM `OriginRef` /
`DestinationRef` derivation may point to the next/previous known stop instead.

**Root cause and why this isn't fixed at the DB level right now:** the `netex_published_journey_track` table's
`planned_track` column is `NOT NULL` (see `V49__netex_published_journey.sql`), and
`NeTExService.buildPublishedJourneyDrafts()` accordingly filters out any commercial stop whose track is unknown
before persisting — there is no other DB table (no separate journey-pattern/route table) that carries the true
origin/destination independently of this filtered track list. Fixing this properly would require a schema
migration (`planned_track` nullable) plus removing that filter, which is a bigger change than its current impact
warrants.

**Decision:** deferred. A NeTEx-side change already in progress (separate branch) will guarantee every published
stop always carries a planned track, which removes the underlying condition (unknown track at an endpoint)
entirely — at that point this can never happen in practice and no DB/schema change is needed here. Revisit only if
that guarantee doesn't materialize or turns out to have exceptions.

## Unknown live-track fallback to the planned track (ET and VM)

### Problem

A colleague (via Slack) reported that when a stop's real-time track/platform is unknown
(`GTFSTimeTableRow.getUnknownTrack() == true` / `GTFSTrainLocation.getUnknownTrack() == true`), the code treated the
stop as entirely unresolved:

- **SIRI-ET** (`EtJourneyInterpreter.resolveStopRef`): returned no `StopRef`, which caused the *whole journey* to be
  dropped from the SIRI-ET output.
- **SIRI-VM** (`VmJourneyConverter.resolveMonitoredCall`): returned no `MonitoredCall`, dropping that element from
  the `VehicleActivity` (the vehicle itself was still emitted, just without its next-stop details).

This was unnecessarily lossy: `unknownTrack=true` means the confirmed real-time track isn't known yet, not that the
platform changed from what was planned. So falling back to the **planned track** (already stored per journey/stop/
visit in `NeTExPublishedJourneyTrack`) recovers a valid `Quay`/`StopPlace` in the common case, instead of silently
losing data.

### `visitIndex`

Both fixes hinge on `visitIndex`: the 0-based occurrence count of a given `stationShortCode` within a journey's
ordered *commercial* stops (rows where the train actually takes on/lets off passengers — see
`EtJourneyInterpreter.isCommercial`/`pairRows`). This is the same key persisted by NeTEx generation in
`NeTExPublishedJourneyTrack.visitIndex`, and is required by `PlannedTrackLookup.plannedTrack(trainNumber,
departureDate, stationShortCode, visitIndex)` to disambiguate a station served more than once on the same journey
(e.g. a turn-back service that visits a station twice with different platforms each time).

### SIRI-ET fix

`EtJourneyInterpreter.interpret(...)` already iterates the train's *entire* `time_table_row` list once, so
`visitIndex` can be tracked incrementally with a simple running count per station
(`resolveVisitCounts.merge(stationShortCode, 1, Integer::sum) - 1`). `resolveStopRef` was changed to fall back to
`plannedTrackLookup.plannedTrack(...)` whenever the live/actual track is null or `unknownTrack=true`, instead of
returning empty.

### SIRI-VM fix — why it needed more than a copy-paste of the ET fix

Unlike ET, `VmJourneyConverter.resolveMonitoredCall` only ever receives a **single** `GTFSTrainLocation` row per
train — the current/next upcoming stop, selected by a native SQL query (`GTFSTrainRepository.getTrainLocations`)
that deliberately excludes already-passed stops (`actual_time is null`) because it only cares about the next stop.
There is no `visitIndex` available "for free" the way there is in ET.

Three options were considered:

1. **Extend the SQL query to also compute `visitIndex`.** Rejected: this would require reimplementing the
   arrival/departure pairing and commercial-stop-counting logic (`pairRows`/`isCommercial`) as SQL window functions,
   across the *entire* train history (not just the not-yet-departed rows the query currently returns) — complex,
   error-prone, and hard to test compared to the equivalent Java code.
2. **Approximate with `visitIndex = 0`.** Rejected: silently wrong for any station visited more than once.
3. **On-demand secondary fetch, only when the live track is unknown** (chosen). Since an unknown live track is a
   rare edge case, the extra cost is acceptable: when (and only when) `unknownTrack=true`, fetch the train's full
   `time_table_row` list (`TimeTableRowsLookup.rowsFor(trainNumber, departureDate)`,
   backed by `GTFSTrainRepository.findBySourceVersionAndIdIn`), pair it into commercial stops with the new shared
   `CommercialStopVisits.of(rows)` utility (mirrors `EtJourneyInterpreter`'s pairing logic so both converters agree
   on what counts as a commercial stop), then resolve the current visit index with
   `CommercialStopVisits.currentVisitIndex(stops, stationShortCode)`.

   `currentVisitIndex` has one subtlety not needed by ET: it cannot just check whether a stop's arrival row has
   `actualTime == null` to decide "not yet completed" — a stop with both an arrival and a departure row is still the
   *current* stop while the train is **dwelling** there (arrival already has an actual time, departure doesn't).
   `Stop.isEligible()`/`isRowEligible()` check each leg (arrival, departure) independently for actual-time/
   cancellation/commercial-stop eligibility — a stop is still current if *either* row, on its own, hasn't finished
   yet (see the class javadoc in `CommercialStopVisits.java` for the full rationale, including why this must be
   per-row rather than per-stop to mirror `GTFSTrainRepository.getTrainLocations`'s own per-row SQL filters).

   `CommercialStopVisits.of()`/`EtJourneyInterpreter.pairRows()` also both had to reconstruct arrival/departure
   pairing order themselves, since `GTFSTrain.timeTableRows` has no `@OrderBy` and JPA doesn't guarantee retrieval
   order. Sorting by `scheduledTime` is not enough on its own when two or more rows share the *exact* same
   scheduled instant (a zero-dwell stop, or a station-boundary tie with zero scheduled transit time) — several
   review rounds went through a few tie-break approaches (a global type-based tie-break, then a station-aware
   pairwise comparator) before landing on the current fix: sort by `scheduledTime` alone, then reconstruct each
   same-instant run's order explicitly by grouping rows by station (`orderRows`/`reorderTiedGroup` in both files).
   See those methods' own javadoc for a worked example and for why a pairwise comparator approach was rejected
   (it violated Java's `Comparator` contract for 3+-way ties). A 90-day production-data check found no actual
   occurrences of even the simplest tie case, so this is a defensive/correctness fix for a scenario not yet
   observed in real data, not a fix for an observed production bug.

   This also meant no new query was needed for the planned-track data itself: `SiriVmGenerationService` already
   loads `NeTExPublishedJourney.tracks` (`findByDatasetVersionAndDepartureDatesFetchTracks`) — the exact same list
   ET uses to build its `MapPlannedTrackLookup` — it just wasn't building the lookup from it. `buildDbSources()` now
   aggregates `tracksByTrainId` the same way `SiriEtGenerationService.buildDbSources` does, and constructs a
   `MapPlannedTrackLookup` passed into `VmJourneyConverter`.

### New shared code

- `siri/common/CommercialStopVisits.java` — pairs a train's raw `GTFSTimeTableRow` list into commercial stops and
  exposes `currentVisitIndex(stops, stationShortCode)`. Used only by the VM fallback path (ET already has its own
  incremental tracking since it processes the full row list up front).
- `siri/common/TimeTableRowsLookup.java` — `@FunctionalInterface rowsFor(trainNumber, departureDate)`; a seam kept
  out of `VmJourneyConverter` so it stays unit-testable without a DB dependency. `SiriVmGenerationService` supplies
  the real DB-backed implementation; tests supply a no-op/fake.

### Status

Implemented and tested. Full SIRI-ET and SIRI-VM test suites pass (see `SIRI.md` for current test counts per
class). The "Origin/Destination endpoint derivation on published journeys" follow-up above documents a separate,
known, deliberately-deferred limitation (unrelated to this fallback) — it is not a test failure.

## PETI stop-source caching and resilience (`CachingPetiStopSource`)

### Lazy initialization

`getStops()` loads the PETI snapshot on demand if it is still empty (e.g. right after an app restart, before the
daily 02:00:30 UTC scheduled `refresh()` has run), so callers (`NeTExService`, `SiriEtGenerationService`,
`SiriVmGenerationService`) never need to call a separate "ensure loaded" step themselves — `PetiStopSource` no
longer exposes one. Concurrent first callers contend on a single lock (`refreshLock`, double-checked) so only one
HTTP fetch happens even if several generation cycles race on first use. An hourly safety-net (`retry-cron`, on
the hour) retries `refresh()` if the last attempt failed, giving the 02:00:30 fetch a real chance to recover by
03:00 — well before the 04:00 UTC NeTEx generation.

### Retry-delay gate (1 minute)

If the initial load fails, a `nextInitialLoadAttempt` gate (1 minute) prevents every subsequent `getStops()` call
from re-attempting the fetch (and re-incurring its timeout) until the gate has passed. The scheduled `refresh()`
always attempts regardless of this gate — it exists only to stop frequent SIRI generation cycles from hammering a
down/slow PETI endpoint.

### Per-fetch retry (transient failures only)

Within a single fetch attempt, transient failures — 5xx responses, connection errors, and a per-attempt timeout
(`updater.netex.peti.request-timeout-seconds`, default 10s) — are retried automatically with a short deterministic
backoff (2 retries, 1s then 2s, capped at 4s; no jitter, since a single internal client doesn't need to desynchronize
from other clients). 4xx responses and parse errors are never retried, since retrying them cannot change the
outcome. `updater.netex.peti.block-timeout-seconds` (default 40s) is the outer bound on the whole fetch including
retries, and must comfortably exceed `request-timeout-seconds × 3` plus backoff.

Failures are always logged: transient errors as `log.error` per attempt-group outcome (`method=refresh
operation=fetchPeti outcome=error`), each retry as `log.warn` (`outcome=retry attempt=…`), and a final `log.warn`
if `getStops()` still returns empty afterward (`method=ensureLoaded outcome=empty`). On any failure, the last-good
snapshot is preserved — generation degrades to stale-but-valid data (or, for SIRI, fails the cycle and keeps the
previous published package) rather than a package with an empty/partial stop assignment.
