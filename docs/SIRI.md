# SIRI (Real-Time Data) — Unified Overview

This document is the single entry point for Digitraffic Rail's SIRI real-time services: **SIRI-ET** (implemented)
**SIRI-VM** (implemented) and **SIRI-SX** (implemented, DPO-4882). It covers
the shared background, the common architecture pattern used by every service, and per-service specifics. It
replaces the need to read scattered planning notes to understand "why is SIRI built this way".

For each service's data source, rules and field mapping, see [`SIRI-ET.md`](SIRI-ET.md), [`SIRI-VM.md`](SIRI-VM.md)
and [`SIRI-SX.md`](SIRI-SX.md).

## Canonical source material

Digitraffic Rail's SIRI documents must conform to the **Norwegian/Nordic SIRI Profile**, published and maintained by
Entur on behalf of Jernbanedirektoratet (the Norwegian Railway Directorate). This is the *authoritative,
living* specification — prefer it over any older/cached notes if the two ever disagree, and use it both
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
  We always emit whole seconds, so the document looks the same whether a time came from the database or from a clock.
- **National stop IDs.** Every stop reference must be the official ID from the national stop place registry —
  for rail, these are PETI IDs: platforms are `FSR:Quay` (ET and VM), and SX station messages
  refer to the PETI stop place (`StopPlaceRef`). SX train messages have only the journey reference and no stops. The same IDs our NeTEx timetable uses.
- **Tied to the plan.** Every real-time item about a train (ET, VM, SX train messages) references IDs already
  published in NeTEx (or SIRI-PT). An item that can't be tied back to a published `VehicleJourney` must not be
  published with a synthetic/fabricated reference — it should be dropped. SX station messages are not about a
  journey, so they have no journey reference.
- **Producer codespace.** Both the delivery's producer reference (`ProducerRef`) and each item's data source
  reference (`DataSource`) use Fintraffic Rail's registered codespace, `FTR`. (Note: `FTR` does not appear on
  Entur's own Norwegian-codespace registry page linked above — that page only lists Norwegian data providers;
  Fintraffic Rail's Finnish national codespace is registered separately and is expected, not a discrepancy.)
- **Data correctness/completeness.** No placeholder/test data in production; each delivered file must be
  self-contained (not depend on other SIRI files to be meaningful); updates should be published as soon as
  feasible after the source data changes (data freshness).

## Common architecture: Converter → domain IR → Marshaller

Every SIRI service in this codebase (ET, VM and SX) is built from the same
three-stage pipeline, split across two Java packages:

- **`service/siri/common`** — collaborators shared by *all* services: `ResolvedJourney` / `LineId` /
  `OperatorRef` / `DataFrameRef` / `ServiceJourneyId` / `StopRef` (value types for published-journey/stop
  identity), `SiriStopResolver` (station+track → PETI `FSR:Quay`), `SiriWritingService` (JAXB marshal + XSD
  schema validation, shared envelope building), `PetiUnavailableException` /
  `PublishedJourneysUnavailableException` (dependency-failure signaling used by every generation service).
- **`service/siri/<service>`** (e.g. `siri/et`, `siri/vm`) — everything specific to that one SIRI service:
  - **Converter** (`EtJourneyConverter`, `VmJourneyConverter`, `SxSituationConverter`):
    decides *what the real-time situation is* — resolves the published journey/stop via `JourneyRefResolver` /
    `StationUicLookup` / `StationNameLookup` / `SiriStopResolver`, applies unit conversions, and returns a small
    immutable **domain record** (`EtCall`-family, `VmActivity`). Has zero dependency on JAXB/`uk.org.siri.*`
    types.
  - **Marshaller** (`EtJourneyMarshaller`, `VmJourneyMarshaller`): pure translation from the domain record to
    the SIRI JAXB object tree (timezone conversion, `BigDecimal`/`Duration` formatting, envelope assembly). No
    conversion logic.
  - **Service** (`SiriEtService`, `SiriVmService`): orchestrates converter + marshaller over a batch of
    trains/locations, and computes per-cycle stats (e.g. how many inputs were dropped as unresolvable) from the
    size of the intermediate domain-record list, before any XML exists.
  - **GenerationService** (`SiriEtGenerationService`, `SiriVmGenerationService`): the full scheduled-cycle
    pipeline — PREPARE (fetch trains/locations + published-journey/PETI dependencies, failing fast via
    `PetiUnavailableException`/`PublishedJourneysUnavailableException` if PETI is completely empty or the
    NeTEx published-journeys snapshot is stale/missing — see the precondition note below, PETI snapshot *age*
    itself is telemetry-only, not a rejection criterion) → BUILD (call the
    Service) → VALIDATE (schema-validate the output) → PERSIST (write to `GeneratedExport` with a
    service-specific filename, e.g. `siri-vm.xml`) → COMPLETE (emit a `rail.siri.generation` wide-event log
    line with a `rail.siri.service=et|vm|sx` tag for observability). Only the newest snapshot is served, so
    `RealtimeFeedCleanupService` deletes snapshots older than an hour (always keeping the newest one), the same
    as for the GTFS Realtime feeds.

**Why this split exists**, rather than one method building JAXB objects directly from the raw data source: it
keeps conversion and marshalling as separate, independently testable concerns (converter unit tests
assert on plain record fields, no XML/JAXB involved); it gives the Service a pre-XML checkpoint to compute
stats from; and it isolates any future SIRI schema-version bump to the Marshaller only. See the `VmActivity`
Javadoc for the fuller rationale (applies equally to ET and SX).

### Why ET, VM and SX are separate Spring beans, not methods on one class

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
- **`RecordedAtTime` per journey.** Each `EstimatedVehicleJourney` reports when its own data last changed, not
  when the file was generated. The value is the newest `modified` of the train and its timetable rows; a row
  can be newer than the train, because track changes and RAMI's unknown-delay/track flags update single rows.
  It is never later than the generation time, and falls back to it when no `modified` is set. The surrounding
  `EstimatedJourneyVersionFrame` keeps the generation time, since that belongs to the whole file.
- **Departure estimates never precede a realised arrival.** When a train has arrived but its departure
  estimate has not been recalculated yet, the source data can briefly hold a departure estimate that is
  earlier than the actual arrival at the same stop. Such an estimate is published at the arrival time instead.

### SIRI-VM (implemented)

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
  train's earliest not-yet-happened-or-stale-estimate commercial `time_table_row`. Since `time_table_row.type`
  (ARRIVAL/DEPARTURE) is now also selected, `VehicleAtStop` is derived from it, but a selected DEPARTURE does
  **not** always mean `true`: if the resolved row is that stop's ARRIVAL, the train hasn't reached it yet
  (`false`); if it's the DEPARTURE, the query additionally checks whether that stop's paired ARRIVAL has
  actually happened (`actual_time` set) or never existed (the journey's origin stop) — only then is the train
  considered at (or not yet having left) that stop (`true`). A DEPARTURE whose paired ARRIVAL is still pending
  (e.g. its live estimate is stale) is **not** at the stop yet, so `VehicleAtStop` is `false` in that case too.
  `VehicleLocationAtStop` is emitted only when `VehicleAtStop=true`, reusing the vehicle's own last
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
    `sequenceIndex` is a dedicated, always-increasing journey-position field (see
    `NeTExPublishedJourneyTrack`), distinct from `visitIndex` (a per-station occurrence counter used for
    keyed lookups): ordering by `visitIndex` alone misplaces a repeated station's later visit.
  - `MonitoredCall.VehicleAtStop`/`VehicleLocationAtStop` — see above.
  - `InCongestion` — derived from `time_table_row.unknown_delay`: set by the source system (RAMI) when it
    cannot reliably estimate how long a train will actually have to wait (`Delay`/the live estimate exist but
    are known to be unreliable). The Nordic SIRI-VM profile has no dedicated "estimate is unreliable" field, so
    this is surfaced via `InCongestion` instead — its wording ("affected by ... other circumstances which may
    lead to further delays") fits an unknown-wait situation reasonably well, and no better-fitting field exists;
    this is an accepted design decision, not a literal congestion signal. `Delay` itself is still always
    computed/emitted as normal (mandatory, 1:1) — `unknown_delay` only adds this extra "don't fully trust it"
    signal alongside it, it never suppresses `Delay`. See `GTFSTrainRepository.getTrainLocations`/
    `VmJourneyConverter`/`VmJourneyMarshaller`.
- **Still not implemented** (all optional per spec; checked against the actual data available and found to have
  no ready source): `Bearing` (no heading in `train_location`), `Occupancy` (no passenger telemetry),
  `VehicleStatus` (no status enum source), `ProgressBetweenStops`
  (investigated in depth — PALA optionally carries a track-km linear position (`ratakmsijainti`) that could
  feed this, but its production reliability is unverified and no station↔track-km reference dataset exists
  to pair it with; see "ProgressBetweenStops" in `SIRI-VM.md` for the full
  analysis and rejected alternatives). `DirectionRef` is optional in VM (unlike ET) and is
  omitted entirely rather than emitting a meaningless `"0"`. See the field-by-field table in
  `SIRI-VM.md` → "Field mapping" for the full implemented/not-implemented breakdown
  with reasoning per field. Revisit any of these if a real data source becomes available (e.g. a heading sensor,
  track-km reference data).
- **Precondition**: the spec states valid timetable data (NeTEx/SIRI-ET) must exist before VM position data is
  sent. This is enforced implicitly: `VmJourneyConverter` drops (returns `Optional.empty()` for) any location
  whose train doesn't resolve to a published `NeTExPublishedJourney` via `JourneyRefResolver`, and
  `SiriVmGenerationService` fails the whole cycle via `PetiUnavailableException` if the PETI stop snapshot
  is completely empty (`PetiUicMatcher.matchedCount() == 0`), or `PublishedJourneysUnavailableException` if
  the NeTEx published-journeys snapshot is missing/empty. Note this is **not** a staleness check: PETI
  snapshot age (`CachingPetiStopSource.getSnapshotAgeSeconds()`) is logged for observability only and is
  never compared against a threshold — an old-but-non-empty PETI snapshot is accepted and used as-is.

### SIRI-SX (DPO-4882)

Situation Exchange — free-text disruption and passenger-information messages (`PtSituationElement`), tied to
the published timetable by reference. SX is real-time in the sense that a message appears, changes and closes
as soon as it does in the source, but unlike ET and VM the content only changes when a message changes. The
document always holds the complete set of valid messages in one XML file.

- **Flags**: `avoindataserver.siri.sx.enabled` (server), `updater.siri.sx.enabled` (updater),
  `updater.siri.sx.fixed-rate-ms` (default 60000). **Endpoint**: `GET /api/v1/siri/sx`. **Generated file**:
  `siri-sx.xml`.
- **Details** (data source, other possible sources, rules, field-by-field status): [`SIRI-SX.md`](SIRI-SX.md).

## PETI stop-source caching and resilience (`CachingPetiStopSource`)

All SIRI services (and NeTEx generation) read PETI stops through `CachingPetiStopSource`.

- **Lazy initialization.** `getStops()` loads the PETI snapshot on first use if it is still empty (for example right
  after a restart, before the daily 02:00:30 UTC `refresh()`). Callers do not need a separate "ensure loaded" step.
  Concurrent first callers share one lock (`refreshLock`, double-checked), so only one HTTP fetch happens. An hourly
  safety net (`retry-cron`, on the hour) retries `refresh()` if the last attempt failed, so the 02:00:30 fetch has a
  real chance to recover by 03:00, before the 04:00 UTC NeTEx generation.
- **Retry-delay gate (1 minute).** If the first load fails, `nextInitialLoadAttempt` stops every later `getStops()`
  call from trying again (and waiting for the timeout) for one minute. The scheduled `refresh()` always tries
  regardless. The gate only protects against SIRI cycles hammering a down or slow PETI.
- **Retry inside one fetch (transient failures only).** 5xx responses, connection errors and a per-attempt timeout
  (`updater.netex.peti.request-timeout-seconds`, default 10 s) are retried twice with a fixed backoff of 1 s then 2 s
  (capped at 4 s, no jitter). 4xx responses and parse errors are never retried. The outer limit for the whole fetch
  including retries is `updater.netex.peti.block-timeout-seconds` (default 40 s); it must be clearly more than
  `request-timeout-seconds × 3` plus backoff.
- **Logging.** Errors are logged per attempt group (`method=refresh operation=fetchPeti outcome=error`), each retry as
  `outcome=retry attempt=…`, and an empty result as `method=ensureLoaded outcome=empty`.
- **Last good snapshot is kept.** On any failure the previous snapshot stays in use. SIRI then serves stale but valid
  data (or the cycle fails and the previous file stays published) instead of a package with missing stops.
- **Stable ids.** `FSR:Quay:*` and `FSR:StopPlace:*` ids do not change over time, so they can be cached across cycles.

## Open items

- Document the SIRI response formats on digitraffic.fi.

## Configuration reference

| Property | Module | Purpose |
|---|---|---|
| `avoindataserver.siri.et.enabled` | AvoinDataServer | Enables `GET /api/v1/siri/et` |
| `avoindataserver.siri.vm.enabled` | AvoinDataServer | Enables `GET /api/v1/siri/vm` |
| `updater.siri.et.enabled` | AvoinDataUpdater | Enables scheduled SIRI-ET generation |
| `updater.siri.et.fixed-rate-ms` | AvoinDataUpdater | SIRI-ET generation interval (default 60000 ms) |
| `updater.siri.vm.enabled` | AvoinDataUpdater | Enables scheduled SIRI-VM generation |
| `updater.siri.vm.fixed-rate-ms` | AvoinDataUpdater | SIRI-VM generation interval (default 60000 ms) |
| `avoindataserver.siri.sx.enabled` | AvoinDataServer | Enables `GET /api/v1/siri/sx` |
| `updater.siri.sx.enabled` | AvoinDataUpdater | Enables scheduled SIRI-SX generation |
| `updater.siri.sx.fixed-rate-ms` | AvoinDataUpdater | SIRI-SX generation interval (default 60000 ms) |
| `updater.realtime-feed.cleanup.fixed-delay-ms` | AvoinDataUpdater | Interval of the realtime feed cleanup, SIRI and GTFS Realtime |
| `updater.realtime-feed.cleanup.retention-minutes` | AvoinDataUpdater | How long realtime feed snapshots are kept; the newest is always kept |
| `updater.realtime-feed.cleanup.batch-size` | AvoinDataUpdater | Maximum number of snapshots deleted per feed per run, in one transaction; a large backlog is deleted over several runs |
| `updater.realtime-feed.cleanup.siri-et-batch-size` | AvoinDataUpdater | Same for SIRI-ET, whose snapshots are about 12 MB each |

## Testing approach (applies to ET, VM and SX)

- **Golden XML tests** (`SiriEtGoldenXmlTest`, `SiriVmGoldenXmlTest`, `SxGoldenXmlTest`): deterministic scenario inputs → compare
  normalized XML output byte-for-byte against a checked-in reference file
  (`src/test/resources/siri/expected-siri-<service>-<scenario>.xml`). When the mapping changes intentionally,
  regenerate from `target/siri-<service>-actual-<scenario>.xml` (written on every run) after manually verifying
  the new output.
- **Service-level unit tests** (`SiriVmServiceTest` etc.): schema validity, unresolved-journey dropping,
  optional-field omission — assert on the domain record and/or the JAXB tree directly, no HTTP involved.
- **Controller integration tests** (`SiriVmControllerIntegrationTest` etc.): HTTP-level behavior with the flag
  explicitly re-enabled (the test `application.properties` shadows the main one and leaves it unset/disabled
  by default) — fresh/stale caching headers, 404 when enabled but nothing has been generated yet. The
  complementary "flag left disabled entirely, route never mapped" case has its own 404 test in a separate class
  (`SiriVmControllerDisabledIntegrationTest`/`SiriEtControllerDisabledIntegrationTest`), since it needs the
  opposite property configuration and thus a separate Spring context.
- **Database tests** (`SiriEtDbIntegrationTest`, `SiriVmDbIntegrationTest`): real database rows, no mocked repositories.
