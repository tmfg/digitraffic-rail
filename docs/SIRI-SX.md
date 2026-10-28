# SIRI-SX (Situation Exchange) Data Mapping

Field-by-field status against the Entur SIRI-SX profile (v1.1,
https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370605/SIRI-SX). The rules behind the mapping (data source,
closing, text cutting, `ReportType`, filtering) are in [`SIRI.md`](SIRI.md), section SIRI-SX.

Status: ✅ implemented, ❌ not implemented (no data or not needed). Cardinality is from the profile
(`1:1` required, `0:1` optional, `1:*` one or more).

## Envelope (`ServiceDelivery` and `SituationExchangeDelivery`)

The messages are wrapped in two levels. `ServiceDelivery` is shared with ET and VM. `SituationExchangeDelivery` is the
SX part, inside it:

```
Siri
└─ ServiceDelivery                  (shared by all SIRI feeds)
   └─ SituationExchangeDelivery     (SX only)
      └─ Situations
         └─ PtSituationElement      (one per message)
```

Fields come partly from parent types in the SIRI schema (`A < B` means A inherits the fields of B). The "Defined in"
column tells which type defines the field. Inheritance is not the same as nesting: `SituationExchangeDelivery` sits
inside `ServiceDelivery` but does not inherit from it.

```
ServiceDelivery < ServiceDeliveryStructure < ProducerResponseStructure < ResponseStructure
SituationExchangeDelivery (SituationExchangeDeliveryStructure) < AbstractServiceDeliveryStructure < ResponseStructure
```

Only fields we set and fields named in the Entur profile are listed. Other schema fields are not used (for example
subscription fields such as `SubscriberRef`, request fields such as `RequestMessageRef`, and `Status`, `ErrorCondition`,
`ValidUntil`, `ShortestPossibleCycle`, `DefaultLanguage`, `Extensions`).

| Field | Defined in | Cardinality | Status | Value |
|---|---|---|---|---|
| `ServiceDelivery/ResponseTimestamp` | `ResponseStructure` | 1:1 | ✅ | When the file was built |
| `ServiceDelivery/ProducerRef` | `ProducerResponseStructure` | 1:1 | ✅ | `FTR` |
| `SituationExchangeDelivery/ResponseTimestamp` | `ResponseStructure` | 1:1 | ✅ | When the file was built |
| `SituationExchangeDelivery/@version` | `SituationExchangeDeliveryStructure` | 0:1 | ✅ | SIRI version of the delivery |
| `SituationExchangeDelivery/Situations/PtSituationElement` | `SituationExchangeDeliveryStructure` | 1:* | ✅ | One per RAMI message |

## Message (`PtSituationElement`)

```
PtSituationElement < SituationElementStructure < AbstractSituationElementStructure
```

The "Defined in" column tells which of these types defines the field. Only fields we set and fields named in the Entur
profile are listed; the schema has many more (for example `Keywords`, `Details`, `Images`, `Consequences`).

| Field | Defined in | Cardinality | Status | Value / reason |
|---|---|---|---|---|
| `SituationNumber` | `AbstractSituationElementStructure` | 1:1 | ✅ | `FTR:SituationNumber:<RAMI message id>` |
| `Version` | `AbstractSituationElementStructure` | 0:1 | ✅ | RAMI `version` |
| `CreationTime` | `AbstractSituationElementStructure` | 1:1 | ✅ | `created_source` of the first version |
| `VersionedAtTime` | `SituationElementStructure` | 0:1 | ✅ | `created_source` of the latest version |
| `ParticipantRef` | `AbstractSituationElementStructure` | 1:1 | ✅ | `FTR` |
| `Source/SourceType` | `SituationElementStructure` | 1:1 | ✅ | `directReport` (required by the format, not used) |
| `Progress` | `PtSituationElement` | 1:1 | ✅ | `closed` if `deleted` is set, otherwise `open` |
| `ValidityPeriod` | `PtSituationElement` | 1:* | ✅ | `start_validity` – `end_validity`. A closed message ends 5 hours after the deletion time |
| `UndefinedReason` | `PtSituationElement` | 1:1 | ✅ | Always empty (required by the format, not used) |
| `ReportType` | `PtSituationElement` | 1:1 | ✅ | Train message `incident`, station message `general` (rough rule, see `SIRI.md`) |
| `Summary` | `PtSituationElement` | 1:* | ✅ | Screen text cut to 160 characters, one per language |
| `Description` | `PtSituationElement` | 0:* | ✅ | Full text, one per language |
| `Affects` | `PtSituationElement` | 1:1 | ✅ | See below |
| `Severity` | `PtSituationElement` | 0:1 | ❌ | No data. Default `normal` applies |
| `Priority` | `PtSituationElement` | 0:1 | ❌ | No data |
| `Planned` | `PtSituationElement` | 0:1 | ❌ | No data (track work notices are planned, but nothing in RAMI says so) |
| `Advice` | `PtSituationElement` | 0:* | ❌ | RAMI texts do not separate advice from the message |
| `InfoLinks` | `PtSituationElement` | 0:1 | ❌ | Links exist only inside the text |

## Target of the message (`PtSituationElement/Affects`)

`Affects` is of type `AffectsScopeStructure`. The "Defined in" column names the type that holds the last part of the
path (the structure the field belongs to). Exactly one of these is used per message.

| Field | Defined in | Status | Used for |
|---|---|---|---|
| `StopPlaces/AffectedStopPlace/StopPlaceRef` | `AffectedStopPlaceStructure` | ✅ | Station messages. PETI stop place of each station in the message |
| `VehicleJourneys/AffectedVehicleJourney` | `AffectedVehicleJourneyStructure` | ✅ | Train messages. `FramedVehicleJourneyRef` (date + journey id), `Operator`, `LineRef` as in ET, and the required empty `Route` |
| `Networks` (network, operator, line) | `AffectsScopeStructure` | ❌ | RAMI does not target a line or the whole network |
| `StopPoints/AffectedStopPoint` | `AffectedStopPointStructure` | ❌ | Platform level. RAMI gives no platform |
| `VehicleJourneys/.../Route/RouteRef` | `AffectedRouteStructure` | ❌ | Only needed when a message affects part of the route. The NeTEx route id exists (journey pattern → route), but it is not stored with the published journeys, and RAMI does not limit a message to part of a route |
| `VehicleJourneys/.../Route/StopPoints`, `Sections` | `AffectedRouteStructure` | ❌ | The station list of a train message is where the message is shown, not the affected stretch. Could later be sent as stop place references |
| `AccessibilityAssessment`, `AffectedComponents` | `AffectedVehicleJourneyStructure`, `AffectedStopPlaceStructure` | ❌ | No accessibility or station-part data |

Everything marked ❌ was checked against the RAMI data available to the updater; none has a ready source today.
