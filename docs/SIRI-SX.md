# SIRI-SX (Situation Exchange)

SIRI-SX publishes situation messages: disruption and passenger information that is shown on station and train
displays and spoken in announcements. The overall SIRI overview is in [`SIRI.md`](SIRI.md).

Flags: `avoindataserver.siri.sx.enabled` (server), `updater.siri.sx.enabled` (updater),
`updater.siri.sx.fixed-rate-ms` (default 60000). Endpoint: `GET /api/v1/siri/sx`. Generated file: `siri-sx.xml`.

Profile: Entur SIRI-SX v1.1, https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370605/SIRI-SX.

## Data source

RAMI messages, the same messages that are shown on station and train displays and spoken in announcements.
Tables: `rami_message` (one row per version), `rami_message_video` (screen text, fi/sv/en), `rami_message_audio`
(spoken text) and `rami_message_station`. Only the latest version of each message counts. Two kinds exist: station
messages (`SCHEDULED_MESSAGE`) and train messages (`MONITORED_JOURNEY_SCHEDULED_MESSAGE`, with train number,
departure date and journey reference).

The situations are read from the source on every run and the whole file is built from them; there is no own table for
them. A source gives the situations that are active now as plain records, so another source can be added later
without changing the rest. `SituationNumber` is `FTR:SituationNumber:<RAMI message id>` (for example
`FTR:SituationNumber:SHM20260901123456789`). RAMI ids already look different from any other source, so no source prefix
is added; if a second source is added, give it its own prefix then. The id is public and must not change once
published.

### RAMI fields and codes

Station codes in the repository:

| Code | Example | Where |
|---|---|---|
| Short code (`Station.shortCode`) | `HKI`, `TPE`, `RI`, `OLLI` | RAMI (`rami_message_station.station_short_code`), 2–4 characters, upper case |
| UIC code (`Station.uicCode`) | HKI = 1, PSL = 10 | Station table, used by ET and VM |
| PETI stop place | station UIC + 1 000 000 | PETI. `PetiUicMatcher` does the conversion. Platforms are `FSR:Quay` |

The chain is RAMI short code → `Station` → UIC → PETI stop place. All 297 codes in the production data exist in the
station table. Of them 206 are stations, 59 are stopping points (for example `KRS`) and 2 are turnouts in the open
line (`KTN`, `OLLI`). A turnout probably has no PETI stop place; such a message is dropped and counted in the log.

Other fields that differ by message:

| Field | Station message | Train message |
|---|---|---|
| `id` | starts with `SHM` | starts with `MVM` |
| `message_type` | `SCHEDULED_MESSAGE` | `MONITORED_JOURNEY_SCHEDULED_MESSAGE` |
| `train_number`, `train_departure_date`, `journey_ref` | always empty | always set (`journey_ref` is for example `100145` for train 145 and is not used) |
| `rami_message_station` | stations where the message applies | stations where the message is shown, not necessarily an affected stretch |

For all messages: `version` starts from 1 and grows by one on every edit, `created_source` is the creation time of
that version, `deleted` is empty while the message is in use, and any language of the screen or spoken text can be
missing (about 15 % of messages have only the spoken text).

### Possible sources for situations

All disruption-type data in the project was reviewed. Only RAMI has ready passenger text. Other sources could be added later.

| Source | What it is | Suitable for SX? |
|---|---|---|
| RAMI messages (`rami_message`) | Ready screen and spoken texts in fi/sv/en, target is a train or a station | **Used** |
| Delay causes (`Cause` on a `TimeTableRow`; `CategoryCode`, `DetailedCategoryCode`, `ThirdCategoryCode`) | Cause codes on a timetable row. The codes have `PassengerTerm` texts in fi/sv/en | Possible. The text is short and the target is a train. Overlaps with RAMI: about 48 % of RAMI train messages start with "Myöhästymisen syy…" |
| Track work notices (`TrackWorkNotification`, RUMA) | Planned track work with time ranges and locations (operating point, section between operating points, geometry) | Target could be found, but there is no passenger text. The notice is made for traffic control and contractors |
| Traffic restrictions (`TrafficRestrictionNotification`, RUMA) | Speed and weight limits with validity time and location | No passenger text, mostly technical content |
| Train running messages (`TrainRunningMessage`) | Train movement on a track section | No. These are position events, not notices |

## Rules

- A message with `deleted` set is published as `closed` for 5 hours after the deletion time (profile
  requirement), then left out.
- `Description` is the full screen text. `Summary` is the same text cut after the last whole word that fits,
  with `...` added, at most 160 characters (profile limit). A text that already fits is used as is.
- Languages fi, sv and en, each with `xml:lang`, written in upper case (`FI`, `SV`, `EN`) because the SIRI 2.0
  schema accepts only upper case codes. A language without text is left out. If there is no screen
  text, the spoken text is used. If there is neither, the message is dropped.
- Station message → `StopPlaces` (PETI stop place of the station; RAMI gives no platform). Train message →
  `VehicleJourneys` (published NeTEx journey, line and operator, like ET). A station message with several
  stations gets the stop places that are found; stations without one are counted in the generation log. A message
  that has no stop place at all, or whose train is not in the published journeys, is dropped and counted in the
  log.
- `ReportType` is `incident` for train messages and `general` for station messages. RAMI has no field for it, so
  this is a rough rule and some messages get the wrong value (for example a train message that only says which
  cars the train has, or a station message about closed platforms).
- Train messages starting with "Seuraava pysähdys" ("next stop") are not published; they are plain display
  information, not situations.
- The XML is built on every run and is deterministic (messages sorted by id, languages in a fixed order, no
  clock times inside a message). If the content is the same as the last saved file, no new file is stored and
  only the time of the last one is updated.
- Train messages are matched to the published NeTEx journeys by the departure dates of the messages (a message can be
  about a train that leaves days ahead). The journeys are not read at all when there are no train messages.
- If the published NeTEx journeys are missing, there are none for a departure date of today or tomorrow that a message needs (dates are Finnish dates, as in NeTEx), or the
  newest of them is more than 30 hours old (same limit as in ET and VM), no journeys are used: train messages are dropped, station messages are still published. The run is
  logged as `partial` with `rail.siri.sx.journeys_unavailable=MISSING|EMPTY|STALE`. Example: the NeTEx run has not worked
  (new environment, NeTEx run is broken, or it has not yet produced today's trains). The feed itself stays fresh
  (`x-is-fresh=true`), so only the log and `SIRI_SX_TRAINS` in `/api/v1/last-updated` show that train messages are
  missing.
- A train message for an older departure date (outside the NeTEx window) is dropped alone when it has no journey; it does not affect other train messages.
- Train messages for a departure date more than one day ahead are not published yet, because NeTEx does not have
  the journeys of that day. This is normal and not an error. The message appears after the NeTEx run that adds the
  day. Station messages are published by their validity time regardless of the departure date.
- Possible simplification: the 30 hour age limit could be dropped from SX, because the per-date check above already
  notices a missing NeTEx day and SX only needs the journey ids. The cost is that a broken NeTEx run is noticed
  1-2 days later. It is kept for now to match ET and VM.
- The run fails, and nothing new is stored, only if the PETI stops are not available (both station and train
  messages need them).

## Field mapping

Status: ✅ in the feed, ❌ not
implemented (no data or not needed). Cardinality is from the profile (`1:1` required, `0:1` optional, `1:*` one or
more).

### Envelope (`ServiceDelivery` and `SituationExchangeDelivery`)

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

### Message (`PtSituationElement`)

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
| `ReportType` | `PtSituationElement` | 1:1 | ✅ | Train message `incident`, station message `general` (rough rule, see Rules above) |
| `Summary` | `PtSituationElement` | 1:* | ✅ | Screen text cut to 160 characters, one per language |
| `Description` | `PtSituationElement` | 0:* | ✅ | Full text, one per language |
| `Affects` | `PtSituationElement` | 1:1 | ✅ | See below |
| `Severity` | `PtSituationElement` | 0:1 | ❌ | No data. Default `normal` applies |
| `Priority` | `PtSituationElement` | 0:1 | ❌ | No data |
| `Planned` | `PtSituationElement` | 0:1 | ❌ | No data (track work notices are planned, but nothing in RAMI says so) |
| `Advice` | `PtSituationElement` | 0:* | ❌ | RAMI texts do not separate advice from the message |
| `InfoLinks` | `PtSituationElement` | 0:1 | ❌ | Links exist only inside the text |

### Target of the message (`PtSituationElement/Affects`)

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
