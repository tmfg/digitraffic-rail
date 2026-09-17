# SIRI (Real-Time Data) — Unified Overview

This document is the single entry point for Digitraffic Rail's SIRI real-time feeds: **SIRI-ET** (implemented,
in master), **SIRI-VM** (implemented, this branch), and **SIRI-SX** (not yet implemented — planned). It covers
the shared background, the common architecture pattern used by every service, and per-service specifics. It
replaces the need to read scattered planning notes to understand "why is SIRI built this way".

For VM-specific implementation history/checklists, see [`SIRI-VM-IMPLEMENTATION-PLAN.md`](SIRI-VM-IMPLEMENTATION-PLAN.md).

## Canonical source material

Digitraffic Rail's SIRI feeds must conform to the **Norwegian/Nordic SIRI Profile**, published and maintained by
Entur on behalf of Jernbanedirektoratet (the Norwegian Railway Directorate). This is the *authoritative,
living* specification — prefer it over any locally-cached PDF snapshot when the two disagree, and use it both
as the basis for new work and for validating existing behavior:

| Topic | URL |
|---|---|
| Space overview | https://entur.atlassian.net/wiki/spaces/PUBLIC/overview |
| General information SIRI (terminology, common components, `ServiceDelivery`, `NaturalLanguageStringStructure`, `FramedVehicleJourneyRefStructure`, data exchange modes) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370373 |
| SIRI-ET (Estimated Timetable) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370392/SIRI-ET |
| SIRI-SX (Situation Exchange) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370605/SIRI-SX |
| SIRI-VM (Vehicle Monitoring) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370425/SIRI-VM |
| SIRI Examples Catalogue (index of example XML files) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370773/SIRI+Examples+Catalogue |
| Codespace registry (`ProducerRef`/`DataSource` values) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370434 |
| Håndbok N801 ("Nasjonale rutedata" — cross-cutting Norwegian rutedata framework; §5.1 covers SIRI/real-time requirements) | https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370406/H+ndbok+N801 |

Example XML files referenced by the Examples Catalogue live in a GitHub repo — **note the correct repo name is
`entur/profile-norway-examples`** (not `entur/profile-examples`, an older/differently-named repo that was used
during initial VM research; verify which is current before relying on it for new work):
https://github.com/entur/profile-norway-examples/tree/master/siri

## Background: what SIRI and the Nordic profile are

**SIRI** (Service Interface for Real Time Information, CEN/TS 15531) is a European standard for exchanging
real-time public transport data — predicted arrival/departure times, cancellations, extra services, platform
changes, vehicle positions, disturbance notices, etc. It is the real-time counterpart to **NeTEx**, which
Digitraffic Rail uses to publish the *planned* timetable. SIRI describes short-term changes to the plan during
an operating day; NeTEx describes the plan itself. Conceptually:

```
NeTEx (long-term planned data)  <  SIRI-PT (short-term planned data)  <  SIRI-ET / VM / SX (real-time data)
```

SIRI is a broad standard with ten functional services (PT, ET, VM, SX, ST, SM, CT, CM, GM, FM). The
**Nordic/Norwegian SIRI Profile** (published by Entur) restricts this to three services relevant to public
real-time consumers, which is also what Håndbok N801 §5.1 requires of any Norwegian real-time data provider:

- **SIRI-ET** — Estimated Timetable: continuous updates to a `VehicleJourney`'s stop times within the current
  operating day (delays, cancellations, additional departures, platform changes). GTFS-Realtime equivalent:
  `TripUpdate`.
- **SIRI-VM** — Vehicle Monitoring: real-time GPS position (and, per the profile, real-time delay) of each
  vehicle. GTFS-Realtime equivalent: `VehiclePosition`.
- **SIRI-SX** — Situation Exchange: free-text disruption/passenger-information messages, tied to affected
  lines/stops/journeys by ID. GTFS-Realtime equivalent: `Alert`.

### Common rules across all three services (from the general SIRI page + Håndbok N801)

- **One file per delivery.** A complete `ServiceDelivery` dataset is delivered as a single XML document.
- **Local time, ISO 8601, ≥ second precision.** All timestamps are local (Helsinki) time, e.g. `2026-07-10T21:22:23`.
- **National stop IDs.** Every stop reference must be the official ID from the national stop place registry —
  for rail, this is the PETI `FSR:Quay` ID (a specific platform), the same IDs our NeTEx timetable uses.
- **Tied to the plan.** Every real-time item references IDs already published in NeTEx (or SIRI-PT). An item
  that can't be tied back to a published `VehicleJourney` must not be published with a synthetic/fabricated
  reference — it should be dropped.
- **Producer codespace.** Both the delivery's producer reference (`ProducerRef`) and each item's data source
  reference (`DataSource`) use Fintraffic Rail's registered codespace, `FTR`. (Note: `FTR` does not appear on
  Entur's own Norwegian-codespace registry page linked above — that page only lists Norwegian data providers;
  Fintraffic Rail's Finnish national codespace is registered separately and is expected, not a discrepancy.)
- **Data correctness/completeness.** No placeholder/test data in production; each delivered file must be
  self-contained (not depend on other SIRI files to be meaningful); updates should be published as soon as
  feasible after the source data changes (data freshness).

## Common architecture: Interpreter → domain IR → Marshaller

Every SIRI service in this codebase (ET today, VM on this branch, SX in the future) is built from the same
three-stage pipeline, split across two Java packages:

- **`service/siri/common`** — collaborators shared by *all* services: `ResolvedJourney` / `LineId` /
  `OperatorRef` / `DataFrameRef` / `ServiceJourneyId` / `StopRef` (value types for published-journey/stop
  identity), `SiriStopResolver` (station+track → PETI `FSR:Quay`), `SiriWritingService` (JAXB marshal + XSD
  schema validation, shared envelope building), `PetiUnavailableException` /
  `PublishedJourneysUnavailableException` (dependency-failure signaling used by every generation service).
- **`service/siri/<service>`** (e.g. `siri/et`, `siri/vm`) — everything specific to that one SIRI service:
  - **Interpreter** (`EtJourneyInterpreter`, `VmJourneyInterpreter`, future `SxSituationInterpreter`): decides
    *what the real-time situation is* — resolves the published journey/stop via `JourneyRefResolver` /
    `StationUicLookup` / `StationNameLookup` / `SiriStopResolver`, applies unit conversions, and returns a small
    immutable **domain record** (`EtCall`-family, `VmActivity`). Has zero dependency on JAXB/`uk.org.siri.*`
    types.
  - **Marshaller** (`EtJourneyMarshaller`, `VmJourneyMarshaller`): pure translation from the domain record to
    the SIRI JAXB object tree (timezone conversion, `BigDecimal`/`Duration` formatting, envelope assembly). No
    interpretation logic.
  - **Service** (`SiriEtService`, `SiriVmService`): orchestrates interpreter + marshaller over a batch of
    trains/locations, and computes per-cycle stats (e.g. how many inputs were dropped as unresolvable) from the
    size of the intermediate domain-record list, before any XML exists.
  - **GenerationService** (`SiriEtGenerationService`, `SiriVmGenerationService`): the full scheduled-cycle
    pipeline — PREPARE (fetch trains/locations + published-journey/PETI dependencies, failing fast via
    `PetiUnavailableException`/`PublishedJourneysUnavailableException` if stale/missing) → BUILD (call the
    Service) → VALIDATE (schema-validate the output) → PERSIST (write to `GeneratedExport` with a
    service-specific filename, e.g. `siri-vm.xml`) → COMPLETE (emit a `rail.siri.generation` wide-event log
    line with a `rail.siri.service=et|vm|sx` tag for observability).

**Why this split exists**, rather than one method building JAXB objects directly from the raw data source: it
keeps interpretation and marshalling as separate, independently testable concerns (interpreter unit tests
assert on plain record fields, no XML/JAXB involved); it gives the Service a pre-XML checkpoint to compute
stats from; and it isolates any future SIRI schema-version bump to the Marshaller only. See the `VmActivity`
Javadoc for the fuller rationale (applies equally to ET, and will apply to SX).

### Why ET and VM (and future SX) are separate Spring beans, not methods on one class

`@ConditionalOnProperty` in Spring only gates **whole bean/class registration** — it cannot conditionally
enable/disable a single `@RequestMapping` method or a single `@Scheduled` method inside an always-registered
class. Since the hard requirement is that each service is independently toggleable
(`avoindataserver.siri.et.enabled` vs. `avoindataserver.siri.vm.enabled`, and equivalently on the updater side),
each service gets:

- its own **controller** class (`SiriEtController` for ET, `SiriVmController` for VM, future `SiriSxController`),
  each `@ConditionalOnProperty(name = "avoindataserver.siri.<service>.enabled")`, serving
  `GET /api/v1/siri/<service>`;
- its own **scheduled updating service** class (`SiriUpdatingService` for ET, `SiriVmUpdatingService` for VM,
  future `SiriSxUpdatingService`), each `@ConditionalOnProperty(name = "updater.siri.<service>.enabled")` with
  its own `@Scheduled(fixedRateString = "${updater.siri.<service>.fixed-rate-ms:60000}")`.

## Per-service status and specifics

### SIRI-ET (implemented, in master)

- **Flags**: `avoindataserver.siri.et.enabled` (server), `updater.siri.et.enabled` (updater).
- **Endpoint**: `GET /api/v1/siri/et`. **Generated file**: `siri-et.xml`.
- **Data source**: `GTFSTrain` + `GTFSTimeTableRow` (the same passenger-filtered, winning-schedule train/stop
  data GTFS-Realtime `TripUpdate` uses), joined against published NeTEx journeys
  (`NeTExPublishedJourneyRepository`) via `JourneyRefResolver`.
  ET always publishes the **complete stop sequence** (`RecordedCalls` + `EstimatedCalls`, chronologically
  ordered) and sets `IsCompleteStopSequence = true` (mandatory per profile, confirmed against the live SIRI-ET
  wiki page).
- `DirectionRef` is mandatory in the SIRI-ET schema but unused by the Norwegian profile; per spec guidance we
  emit the fixed value `"0"`.

### SIRI-VM (implemented, this branch — `feature/DPO-4881-SIRI-VM`)

- **Flags**: `avoindataserver.siri.vm.enabled` (server), `updater.siri.vm.enabled` (updater) — independent of
  the ET flags, per the original requirement.
- **Endpoint**: `GET /api/v1/siri/vm`. **Generated file**: `siri-vm.xml`.
- **Data source**: `GTFSTrainLocation` (via `TrainLocationRepository.findLatestForPassengerTrains` →
  `GTFSTrainRepository.getTrainLocations`) — the same `train_location` table GTFS-Realtime `VehiclePosition`
  already uses. The native query's window-function also resolves the upcoming commercial stop (station +
  track) and, as of this branch, the real-time delay against that stop's scheduled time
  (`TIMESTAMPDIFF(SECOND, scheduled_time, live_estimate_time)`), reused for the `Delay` field below.
- **`MonitoredCall` direction — confirmed against real Entur example files** (`siri-vm-before-stop.xml`,
  `siri-vm-at-stop.xml` in the Examples Catalogue repo): despite the wiki's `MonitoredCallStructure` prose
  reading ambiguously ("most recent (if en route) or current (if stopped) call"), the actual examples show
  `MonitoredCall` always refers to the stop the vehicle is **approaching or currently at** (`VehicleAtStop`
  distinguishes the two) — i.e. the *upcoming or currently-dwelt-at* stop, matching this implementation's
  choice of "next commercial stop" from the SQL query.
- **`VehicleAtStop`/`VehicleLocationAtStop` implemented**: the SQL query's resolved row is always the
  train's earliest not-yet-happened commercial `time_table_row`. Since `time_table_row.type` (ARRIVAL/DEPARTURE)
  is now also selected, `VehicleAtStop` is derived directly from it: if the resolved row is that stop's ARRIVAL,
  the train hasn't reached it yet (`false`); if it's the DEPARTURE — because the ARRIVAL already happened
  (dwelling) or never existed (the journey's origin stop) — the train is at (or hasn't yet left) that stop
  (`true`). `VehicleLocationAtStop` is emitted only when `VehicleAtStop=true`, reusing the vehicle's own last
  reported GPS fix (no more precise "at platform" position exists).
- **Mandatory fields confirmed against the live SIRI-VM wiki page** (fixed during the wiki-crawl review):
  - `Delay` is **mandatory (1:1)**, defined as `"PT0S"` when there is no delay — it must never be omitted. Fixed
    to always emit (`Duration.ZERO` fallback) rather than being conditionally set only when known.
  - `IsCompleteStopSequence` is **mandatory (1:1)** and, since VM only ever reports a single `MonitoredCall`
    (never a full stop sequence like ET), must **always be `false`**. This was previously not set at all; fixed
    to always emit `false`.
- **Optional fields added once a ready data source was identified**:
  - `Velocity` (`xsd:nonNegativeInteger`) is populated from the same km/h→m/s conversion already computed (and
    previously unused) in `VmActivity.speedMetersPerSecond()`.
  - `OriginRef`/`OriginName` and `DestinationRef`/`DestinationName` are resolved from the published journey's
    first/last commercial stop (`ResolvedJourney.origin()`/`.destination()`, sourced from the already-fetched
    `NeTExPublishedJourneyTrack` list — the same fetch-joined tracks `findByDatasetVersionAndDepartureDatesFetchTracks`
    already loaded for dataset-version bookkeeping, just not previously read). Uses the stop's *planned* track,
    not a live position, since a train doesn't have a live position at its origin once en route, nor before it
    reaches its destination. `NeTExPublishedJourney.tracks` now has an explicit `@OrderBy("sequenceIndex ASC")`
    so the first/last element reliably matches true journey order — previously relied on unspecified JPA
    collection order, which was safe for existing per-station keyed lookups but not for "first/last" access.
    `sequenceIndex` is a dedicated, monotonically increasing journey-position field (see
    `NeTExPublishedJourneyTrack`), distinct from `visitIndex` (a per-station occurrence counter used for
    keyed lookups): ordering by `visitIndex` alone misplaces a repeated station's later visit.
  - `MonitoredCall.VehicleAtStop`/`VehicleLocationAtStop` — see above.
- **Still not implemented** (all optional per spec; checked against the actual data available and found to have
  no ready source): `Bearing` (no heading in `train_location`), `Occupancy` (no passenger telemetry),
  `VehicleStatus` (no status enum source), `InCongestion` (no congestion data), `ProgressBetweenStops`
  (investigated in depth — PALA optionally carries a track-km linear position (`ratakmsijainti`) that could
  feed this, but its production reliability is unverified and no station↔track-km reference dataset exists
  to pair it with; see "ProgressBetweenStops investigation" in `SIRI-VM-IMPLEMENTATION-PLAN.md` for the full
  analysis and rejected alternatives). `DirectionRef` is optional in VM (unlike ET) and is
  omitted entirely rather than emitting a meaningless `"0"`. See the field-by-field table in
  `SIRI-VM-IMPLEMENTATION-PLAN.md` → "SIRI-VM Data Mapping" for the full implemented/not-implemented breakdown
  with reasoning per field. Revisit any of these if a real data source becomes available (e.g. a heading sensor,
  congestion feed, or track-km reference data).
- **Precondition**: the spec states valid timetable data (NeTEx/SIRI-ET) must exist before VM position data is
  sent. This is enforced implicitly: `VmJourneyInterpreter` drops (returns `Optional.empty()` for) any location
  whose train doesn't resolve to a published `NeTExPublishedJourney` via `JourneyRefResolver`, and
  `SiriVmGenerationService` fails the whole cycle via `PetiUnavailableException`/
  `PublishedJourneysUnavailableException` if the PETI/NeTEx dependencies are stale or missing.

### SIRI-SX (not yet implemented)

Situation Exchange — free-text disruption messages (`PtSituationElement`), tied by reference to affected
`Networks`/`StopPlaces`/`StopPoints`/`VehicleJourneys`. Key points from the wiki spec, for when this is
implemented:

- Root delivery type: `SituationExchangeDelivery` → one or more `PtSituationElement`. Each situation has a
  **globally unique** `SituationNumber` (format `CODESPACE:SituationNumber:ID`, e.g. `FTR:SituationNumber:123`).
- `Progress` (`open`/`closed`) — a `closed` situation is expired and must not be shown to the public; the
  `ValidityPeriod`'s `EndTime` must be **at least 5 hours in the future** at the moment a situation is closed,
  to ensure downstream systems have time to pick up the cancellation before it stops being redistributed.
- `Severity` (`noImpact`…`verySevere`, default `normal`), `Priority` (1–10, 1 = highest), `ReportType`
  (`general` vs `incident`), `Summary` (**max 160 characters**, one per language), optional `Description` /
  `Advice` / `InfoLinks`.
- `Affects` describes scope: `Networks` (operator/line/mode), `StopPlaces`, `StopPoints`, or `VehicleJourneys` —
  can only be empty when `Progress = closed`.
- No current Digitraffic Rail data source has been identified for disruption/situation text — this is the main
  open question before implementation can start (where would `PtSituationElement` content come from?).

## Configuration reference

| Property | Module | Purpose |
|---|---|---|
| `avoindataserver.siri.et.enabled` | AvoinDataServer | Enables `GET /api/v1/siri/et` |
| `avoindataserver.siri.vm.enabled` | AvoinDataServer | Enables `GET /api/v1/siri/vm` |
| `updater.siri.et.enabled` | AvoinDataUpdater | Enables scheduled SIRI-ET generation |
| `updater.siri.et.fixed-rate-ms` | AvoinDataUpdater | SIRI-ET generation interval (default 60000 ms) |
| `updater.siri.vm.enabled` | AvoinDataUpdater | Enables scheduled SIRI-VM generation |
| `updater.siri.vm.fixed-rate-ms` | AvoinDataUpdater | SIRI-VM generation interval (default 60000 ms) |

## Testing approach (applies to ET, VM, and future SX)

- **Golden XML tests** (`SiriEtGoldenXmlTest`, `SiriVmGoldenXmlTest`): deterministic scenario inputs → compare
  normalized XML output byte-for-byte against a checked-in reference file
  (`src/test/resources/siri/expected-siri-<service>-<scenario>.xml`). When the mapping changes intentionally,
  regenerate from `target/siri-<service>-actual-<scenario>.xml` (written on every run) after manually verifying
  the new output.
- **Service-level unit tests** (`SiriVmServiceTest` etc.): schema validity, unresolved-journey dropping,
  optional-field omission — assert on the domain record and/or the JAXB tree directly, no HTTP involved.
- **Controller integration tests** (`SiriVmControllerIntegrationTest` etc.): HTTP-level behavior — fresh/stale
  caching headers, 404 when disabled.
- Not yet covered for VM: a DB-level end-to-end integration test equivalent to `SiriEtDbIntegrationTest` (see
  `SIRI-VM-IMPLEMENTATION-PLAN.md` Phase 4).
