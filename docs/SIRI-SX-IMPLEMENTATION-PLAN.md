# SIRI-SX (Situation Exchange) Implementation Plan — DPO-4882

> Working document for the DPO-4882 branch. **Delete this file before the release.**
> Background and shared rules: [`SIRI.md`](SIRI.md). Same pattern as ET and VM:
> [`SIRI-VM-IMPLEMENTATION-PLAN.md`](SIRI-VM-IMPLEMENTATION-PLAN.md).

> The permanent description (data source, rules, field mapping) lives in `SIRI-SX.md`; `SIRI.md` has only a summary.
> Keep them in step while working; this plan is deleted before the release.

## Goal

Publish disruption messages as a SIRI-SX feed:

- `GET /api/v1/siri/sx` (server), generated file `siri-sx.xml`.
- Switched on and off on its own, like ET and VM:
  `avoindataserver.siri.sx.enabled` and `updater.siri.sx.enabled`
  (interval `updater.siri.sx.fixed-rate-ms`, default 60000).
- Output is checked against the SIRI 2.0 XSD before it is saved.

## Rules for this ticket

- Keep it simple. Only handle the cases we actually have data for.
- No commits or pushes without asking.
- Short commit messages, in English, no co-author lines.
- Do not touch or close PR comments.

## Data source: RAMI messages (checked from the production read-only DB, 2026-10-27)

Tables: `rami_message` (+ `_video`, `_audio`, `_station`). Sample exports are in `tmp/rami/` (not committed).

- A message is identified by `id` + `version`. Every edit adds a new version row. Only the **latest version**
  counts.
- `deleted` is set when the message is removed (the latest version can be deleted: ~5300 of ~31400 messages).
  Deleted message = `Progress=closed`.
- Two kinds (`message_type`):
  - `SCHEDULED_MESSAGE` (`SHM...`): for stations, no train. Validity from days to months. Examples: track work,
    closed platform, replacement buses.
  - `MONITORED_JOURNEY_SCHEDULED_MESSAGE` (`MVM...`): for one train (`train_number`, `train_departure_date`,
    `journey_ref` e.g. `100944`). Examples: "cars 6 and 7 are missing", "reason for the delay: technical fault".
- Both kinds list stations in `rami_message_station` (`station_short_code`, e.g. `HKI`). 1 to 20 stations per message.
- Texts for passengers are in `rami_message_video` (fi / sv / en): what the displays show. `rami_message_audio`
  is the spoken version. Often the same text, but about 15 % of messages have only the spoken text.
- Right now: 35 messages are valid and not deleted (16 station messages, ~19 train messages).
- Texts are often longer than 160 characters (about 14 % of rows, max 581). SIRI Summary allows only 160.
- Not needed: `rami_stop_monitoring_message` (raw JSON of platform info), `rami_udot*` (already used for the
  unknown delay / track flags in ET and VM).

## Decisions (agreed 2026-10-27)

1. **Long texts:** `Description` is always the full text. `Summary` is the text cut after the last whole word that
   fits, with `...` added at the end, so that the whole `Summary` is at most 160 characters. A text that already
   fits is used as is, without `...`.
2. **Languages:** fi, sv and en, each with `xml:lang`. A language without text is left out.
3. **Station messages:** `Affects/StopPlaces`, station level only (RAMI gives no platform). Platform only if the
   specification forces it.
4. **Train messages:** `Affects/VehicleJourneys`, using the published NeTEx journey like ET. A message whose train
   is not in the published data is dropped. The updater logs a warning with how many were dropped (and which
   message ids).
5. **ReportType:** SIRI-SX has only two values: `general` (information that does not change how the service runs,
   e.g. "no food service on this journey") and `incident` (affects the service, e.g. delays, cancellations,
   replacement buses). RAMI has no such field. Decided in a meeting: every message is `general` (see the Rule below for the earlier idea: train = `incident`).
   This is rough, so some messages get the wrong value (the real data does not split cleanly by type):
   - Train messages that are really only information, so `general` would be right: "Seuraava pysähdys:
     Oulunkylä." (2 of ~28 000), also car order notes like "Junassa vaunut 1-6 Kajaaniin ja vaunut 7-12
     Joensuuhun." (part of the ~1 850 car messages). They get `incident`.
   - Station messages that really affect traffic, so `incident` would be right: track work notices, "Raide on
     suljettu", "junat korvattu bussilla". They get `general`.
   - Not mapped by the rule: nothing is left without a value, but the value can be wrong for the cases above.
   - "Seuraava pysähdys" ("Next stop: ...") is not a disturbance notice but a passenger information line for
     the displays. These messages are left out of the feed (see decision 8).
6. **Deleted messages:** stay in the feed as `closed` for 5 hours after the deletion time, with `EndTime` at
   least 5 hours ahead. After that they are left out.
7. **Missing screen text:** about 15 % of messages have no `rami_message_video` text, only the spoken text in
   `rami_message_audio`. Use the spoken text in that case. If both are empty, drop the message.
8. **Filter out "next stop" messages:** a train message whose Finnish text starts with "Seuraava pysähdys" is not
   published. Only 2 such messages exist in the whole history, so the filter is a single text check, and it is
   kept in one place so more phrases can be added later. The dropped count is included in the generation log.
9. **Station list of a train message:** not used for `Affects` in the minimum version. The train itself is the
   target. The list seems to be the stations where the message is shown on displays (70 % of train messages list
   more than two stations, e.g. all remaining stops of the train), not the affected stretch, so using it could
   say more than the message means. Technically it would be possible later: `VehicleJourneys/AffectedVehicleJourney/
   Route/StopPoints/AffectedStopPoint` accepts a stop place (station) reference when the platform is not known, so
   no platform data is needed (the profile: "If the quay is currently unknown ... a reference to StopPlace may be used").

10. **Saving when nothing changed:** the feed is built on every run (default 60 s). The new XML is compared with
    the last saved `siri-sx.xml`, ignoring the two `ResponseTimestamp` values. If the content is the same, no new
    file is saved; the `created` time of the last file is updated instead, because the server uses it for
    `x-timestamp` and `x-is-fresh`. If the content differs, a new file is saved.
    For the comparison to work, the output must be **deterministic** (same data gives the same bytes):
    - messages sorted by message id (`SituationNumber`), always in the same order
    - per message: languages always in the order fi, sv, en; one `Summary` / `Description` per language
    - no times taken from the clock inside a message: `CreationTime`, `VersionedAtTime` and `ValidityPeriod` come
      from the data (a closed message ends at deletion time + 5 h, not "now + 5 h")
    - only the two `ResponseTimestamp` values come from the clock
    - the XML writer is the same one ET and VM use, so formatting is stable
    - a test builds the feed twice from the same data and checks that the bytes are equal
    Things that change the content with time only (a message starts, ends, or a closed message passes its 5
    hours) are noticed automatically, because the feed is rebuilt every run.

11. **Operator and line on train messages:** included. `Operator/OperatorRef` and `LineRef` come from the same
    resolved journey as in ET and VM (`ResolvedJourney`), so all three feeds agree. Both are optional in the profile
    (for readability); no extra lookups are needed.

## SIRI-SX structures: used and not used

| Structure | Used | Why |
|---|---|---|
| `SituationExchangeDelivery` | Yes | Envelope for the messages |
| `PtSituationElement` | Yes | One message |
| `SituationSource` | Yes, fixed | Profile requires `SourceType=directReport`; nothing else filled |
| `HalfOpenTimestampRangeStructure` | Yes | `ValidityPeriod`: start and end time |
| `Affects` | Yes | Required; may be empty only for a `closed` message |
| `AffectedStopPlace` | Yes | Station messages: `StopPlaceRef` = PETI stop place of the station |
| `AffectedVehicleJourney` | Yes | Train messages: day + journey id like ET, `Operator`, `LineRef`, and the required empty `<Route/>` |
| `InfoLinks` / `InfoLink` | No | Links only exist inside the text |
| `AffectedNetwork`, `AffectedOperatorStructure`, `AffectedLineStructure` | No | RAMI does not target a line or the whole network |
| `AffectedRoute`, `AffectedRouteStructure` | No | Only needed if a message affected part of a route |
| `AffectedSection`, `IndirectSectionRef` | No | A stretch (first-last quay): RAMI gives no stretch |
| `AffectedStopPoint` | No | Platform level: no platform in RAMI |
| `AccessibilityAssessment`, `AccessibilityLimitation` | No | No accessibility data |
| `AffectedComponent` | No | Part of a station (platform, lift): no data |

If none of the stations of a message is found in PETI, the message is dropped and counted in the log, like a train
message whose journey is not published. If only some are found, the message keeps those and the others are counted
(agreed while implementing step 4, because a station message can list up to 20 stations).

## What is mapped and what is left out

| SIRI-SX field | From RAMI | Status |
|---|---|---|
| `SituationNumber` | `FTR:SituationNumber:<id>` | Mapped |
| `Version` | `version` | Mapped |
| `CreationTime` | `created_source` of the first version | Mapped |
| `VersionedAtTime` | `created_source` of the latest version | Mapped |
| `ParticipantRef`, `ProducerRef` | `FTR` | Fixed value |
| `Source/SourceType` | `directReport` | Fixed value (profile requires it) |
| `UndefinedReason` | empty element | Fixed value (profile requires it) |
| `Progress` | `deleted` set = `closed`, else `open` | Mapped |
| `ValidityPeriod` | `start_validity`, `end_validity` (closed: deletion + 5 h) | Mapped |
| `ReportType` | always `general` | Mapped (decided in meeting, see decision 5) |
| `Summary` | screen text, cut to 160 characters + `...` | Mapped |
| `Description` | screen text, full | Mapped |
| `Affects` | train: `VehicleJourneys` (+ `Operator`, `LineRef`); station: `StopPlaces` | Mapped |
| `Severity` | | Left out (no data, profile default `normal`) |
| `Priority` | | Left out (no data) |
| `Planned` | | Left out (no data; track work notices are planned, but nothing says so) |
| `Advice` | | Left out (RAMI texts do not separate advice from the message) |
| `InfoLinks` | | Left out (links, e.g. vr.fi, are only inside the text) |
| `Affects` for a train: stations of the message | `rami_message_station` | Not used (decision 9; could later become `Route/StopPoints` with station references) |
| Train messages starting "Seuraava pysähdys" | | Filtered out (decision 8) |
| Audio delivery times, repeat settings, `days_of_week` | | Not used (how and when to show, not content) |

## Real data examples (production DB, 2026-10-27)

Times in the DB are UTC. Numbers are for the latest version of each message.
More samples are in `tmp/rami/` (not committed).

### How the data looks

| Thing | Number |
|---|---|
| Messages (latest versions) | 31 434 (28 025 train, 3 409 station) |
| Valid now and not deleted | 35 (21 train, 14 station, counting only ones with end time ahead) |
| Deleted | about 5 300 |
| Fi / sv / en screen text present | most; about 15 % have no screen text |
| No text at all (screen or spoken) | 81 (77 of them deleted) |
| Messages without a station | 0 (every message has at least one station) |
| Most versions of one message | 570 (an old station message that is edited all the time) |
| Same train with two valid messages now | 0 |

Train message texts (fi), roughly: 48 % "Myöhästymisen syy: ..." (delay reason), 12 % replaced by bus, 6 % car
changes, the rest other. Only 2 of the current ones are plain "next stop" messages.

### Example cases and what the decisions give

**1. Short train message (delay reason)**
`MVM20261027117364908` v1, train 3 (departure date 2026-10-27, `journey_ref` 100003), station `TKL`
fi: "Myöhästymisen syy: tekninen vika." / sv: "Orsak till förseningen: tekniskt fel." / en: "Reason for the delay: technical fault."
→ `VehicleJourneys` (train 3), `ReportType=incident`. The text is under 160 characters, so `Summary` and
`Description` both carry the full text.

**2. Train message that is not a disturbance**
`MVM20261027121491910` v1, train 8673, station `TKL`: "Seuraava pysähdys: Oulunkylä."
→ Left out: train messages starting with "Seuraava pysähdys" are plain information, not a disturbance
(decision 8). The message is counted as `nextStop`.

**3. Train message with a stop list**
`MVM20261021191628891` v1, train 140, stations `JY,JÄS`: "S140 korvataan bussilla Jyväskylä - Orivesi välillä.
Bussit lähtevät ... Syynä ratajärjestelmävika." → `incident`. Note that the message lists two stations, but the
train is the target: station list is not used for train messages.

**4. Station message, short**
`SHM20260428202859697` v21, station `KV`: "Matkustajalaitureilla tehdään rakentamistöitä 27.7.-30.10." /
sv: "Byggarbeten kommer att utföras på plattformarna från till den 30.10." (the date is missing in the source) /
en: "Construction work will be carried out on the passenger platforms until  Oct 30." → `StopPlaces` (`KV`),
`general`. Texts are used as they are; mistakes in the source texts stay (also the double space).

**5. Long station message (362 characters in fi)**
`SHM20260402953378234` v55, station `RI`: "Ratatyötiedote: 26.-28.10. I-, P-, K- ja T-junat eivät pysähdy Käpylässä, ..."
- `Description` (fi) = the full 362 characters.
- `Summary` (fi) = "Ratatyötiedote: 26.-28.10. I-, P-, K- ja T-junat eivät pysähdy Käpylässä, Oulunkylässä, Pukinmäessä, Malmilla,
  Tapanilassa eivätkä Puistolassa kello..." (151 characters)
- The sv and en texts are cut in the same way (158 and 156 characters).
- The station is `RI` (Riihimäki) although the text is about Helsinki area stations: station list and text do
  not always match, we only follow the station list.

**6. Message with spoken text only**
`SHM20240103657492820` v570: no screen text; spoken text "Huomio! Pahoittelemme testauksesta aiheutuvaa häiriötä.
Testaamme kuulutusjärjestelmää. ..." (a test message that is still valid today).
→ Spoken text is used as the text. Note: test messages like this would be published too.

**7. Deleted message**
`SHM20261014124721229` v2, station `SAV`, deleted 2026-10-24 08:11 (valid until 2026-10-25 21:59):
"Ratatyötiedote: K-junien vuoroväli on tavallista harvempi 25.10. asti. ..."
→ `Progress=closed` until 2026-10-24 13:11 (5 hours after the deletion), then dropped. Most deleted messages
(3 156 of 5 327) were deleted before their end time. The `EndTime` of a closed message is the deletion time + 5 hours.

### ReportType: can anything in the data tell it better?

Looked at: `message_type`, screen `delivery_type` (WHEN / CONTINUOS_VISUALIZATION), spoken `delivery_type`
(NOW / REPEAT_EVERY / ON_EVENT ...), `event_type` (ARRIVING / DEPARTING), number of stations, validity length.

- **No field says "incident or general".** The delivery fields only describe *how* a screen or speaker shows the
  message (repeat every N minutes, when the train arrives, ...), not what it is about.
- Train messages are mostly operational: delay reasons (about half), bus replacement, missing cars. Rule
  `incident` is right for almost all of them. The only exceptions seen are things like "Seuraava pysähdys".
- Station messages are mixed. In the sample: track work notices and "replaced by bus" (affect traffic), "track
  closed" (affects traffic), but also "IC 37 leaves from track 8 at about 13:36" (only information) and the
  audio test message.
- The only way to separate them better is by keywords in the text (e.g. "korvattu bussilla", "peruttu",
  "suljettu", "myöhässä"). Decided against: three languages, free text, would need constant tuning.

**Result:** keep train = `incident`, station = `general`. If a better split is wanted later, keywords in the Finnish
text would be the only way.

## Plan

Each step is small and can be checked on its own.

### Step 1 — Fix validation logging (done)
- Error text says "SIRI 2.0 XSD validation" instead of "structural (JAXB) validation".
- Validation log lines carry `rail.siri.feed=et|vm|sx`: `SiriWritingService.isSchemaValid(xml, feed)` takes the
  feed name from the caller (SX passes `"sx"`).
- Tests updated for the new parameter; SIRI tests pass.

### Step 2 — Data source (done: RAMI tables, see above) and the decisions above

### Step 3 — Read the messages (done)
- Situations are read from the sources on every run and built into the file as a whole. No own table: the file is
  already the stored result, and a copy of the source data would need syncing (deletions, versions, errors).
- Small interface: each source returns the situations that are active now (or closed and still inside the 5 hour
  window) as `SxSituation` records. A deleted message counts only if it was deleted before it expired. `SiriSxGenerationService` collects them, sorts them and writes the XML. Only one
  method, nothing more.
- First and only source now: RAMI (entities and repository for `rami_message` with video, audio and station texts,
  latest version only). Other possible sources are listed in `SIRI-SX.md` ("Possible sources for situations") and are
  not part of this ticket.
- `SituationNumber` = `FTR:SituationNumber:<RAMI id>`, no source prefix (decided: RAMI ids already differ). Add one
  if another source is added.

### Step 4 — Convert and write (done)
- `SxSituationConverter`: message → small plain record (`SxSituation`), no JAXB types.
  - `SituationNumber` = `FTR:SituationNumber:<id>`
  - `Progress` open / closed, `ValidityPeriod`, `Summary` (cut to 160 characters), optional `Description`
  - `Affects` from the chosen scope; messages that cannot be tied to anything are dropped
- `SxSituationMarshaller`: record → SIRI JAXB tree (`SituationExchangeDelivery`, `PtSituationElement`).
- Shared envelope and validation through `SiriWritingService` (`feed` = `sx`).

### Step 5 — Generate on a schedule (done)
- `SiriSxGenerationService` (prepare → build → validate → save → log),
  `SiriSxUpdatingService` (`@Scheduled`, only when `updater.siri.sx.enabled=true`).
- Saved as `GeneratedExport` with the name `siri-sx.xml`, only when the content changed (decision 10); otherwise only
  the `created` time of the last file is updated.
- Log line `rail.siri.generation` with `rail.siri.service=sx`.

### Step 6 — Serve it (done)
- `SiriSxController`, only when `avoindataserver.siri.sx.enabled=true`; same caching headers as ET / VM;
  404 when nothing has been generated yet.

### Step 7 — Update times (done)
- ET, VM and SX are added to the existing `GET /api/v1/last-updated` (Updater) as `SIRI_ET`, `SIRI_VM`, `SIRI_SX_STATIONS`, `SIRI_SX_TRAINS`
  (`LastUpdateService`, alarm limit 5 minutes).
- Each generation service marks its feed updated after a successful run. SX also marks it when the content did not
  change (only the creation time is refreshed). Failed runs do not mark it.
- SX has two keys: `SIRI_SX_STATIONS` is marked after every successful run; `SIRI_SX_TRAINS` only when the train
  messages could be handled (NeTEx journeys available and fresh, or no train messages). When NeTEx is missing or
  stale it goes out of date after 5 minutes from the start (or its last update) while the station messages stay up to date.
- At startup every data type is listed in `last-updated`, so a type that never gets updated becomes outdated after
  its alarm limit. The start time is used for real-time types. GTFS and the SIRI feeds (when enabled with
  `updater.siri.*.enabled`) use the creation time of their latest stored file. Types updated rarely (stations,
  operators, time table periods, track sections, rules, category codes, localizations, future and old trains) are
  stored in the new table `last_update` (migration V53) and read from there, so an old update time is not lost on
  restart. SIRI types of disabled feeds are not listed. The unused type `FORECASTS` was removed.
  `TRAINS_DUMP` and `COMPOSITIONS_DUMP` (monthly S3 dumps made on the 5th, alarm limit 34 days) are now filled in
  like `TRAIN_LOCATIONS_DUMP`.

### Step 8 — Tests and docs (done)
- Golden XML test, service test (valid XML, dropped messages), controller tests (enabled / disabled).
- In `SIRI-SX.md` (Field mapping) remove the note that the ✅ marks are planned, not yet in code.
- Update `SIRI-SX.md` and `SIRI.md` (SX summary, configuration table) and the Swagger / API docs if the endpoint is listed there.

## Progress

| Step | Status |
|---|---|
| 1. Validation logging | Done |
| 2. Data source | Done: RAMI tables, decisions agreed |
| 3. Read messages | Done: `RamiMessageRepository` (latest active versions, first creation time), tested |
| 4. Convert and write | Done: `SxSituationConverter`, `SxSituationMarshaller`, XSD-valid, tested |
| 5. Scheduled generation | Done: `SiriSxGenerationService` (includes convert + write), `SiriSxUpdatingService`, tested |
| 6. Serve it | Done: `SiriSxController`, enabled and disabled tests |
| 7. Update times | Done: `SIRI_ET`, `SIRI_VM`, `SIRI_SX_STATIONS`, `SIRI_SX_TRAINS` in `/api/v1/last-updated` |
| 8. Tests and docs | Done: `SxGoldenXmlTest` (3 scenarios), `SIRI.md` and `SIRI-SX.md` updated |
