# GTFS scripts

Helper script for reading the Digitraffic Rail GTFS and GTFS Realtime APIs from the
command line. The realtime APIs serve binary [protobuf](https://protobuf.dev/) payloads
which are not human readable, so `gtfs.ts` decodes them into JSON.

## Requirements

- Node.js 20 or newer (uses the built-in `fetch` and top-level `await`)
- [pnpm](https://pnpm.io/)

## Setup

```bash
cd scripts/gtfs
pnpm install
pnpm run check             # format:check + typecheck + audit
```

## Updating dependencies

```bash
pnpm outdated                    # list dependencies with newer versions
pnpm update --latest             # bump all dependencies
pnpm update --latest typescript  # bump a single dependency
pnpm run check                   # format:check + typecheck + audit
pnpm gtfs                        # smoke test
```

## Usage

```bash
pnpm gtfs [options]
```

```bash
pnpm gtfs -h
```

| Option | Description |
| --- | --- |
| `-e`, `--env <tst\|prd>` | Environment to query. Default `prd`. |
| `-a`, `--api <api>` | API to fetch. Default `locations`. |
| `-d`, `--date <YYYY-MM-DD>` | Fetch data for a given date, via the `on_date` query parameter. Defaults to the current date. |
| `-o`, `--out <file>` | Write output to a file instead of stdout. Required for the zip APIs. |
| `-h`, `--help` | Show help. |

### Environments

| Value | Host |
| --- | --- |
| `prd` | `https://rata.digitraffic.fi` |
| `tst` | `https://rata-beta.digitraffic.fi` |

### APIs

| Value | Endpoint | Output |
| --- | --- | --- |
| `locations` | `/api/v1/trains/gtfs-rt-locations` | JSON |
| `updates` | `/api/v1/trains/gtfs-rt-updates` | JSON |
| `all-zip` | `/api/v1/trains/gtfs-all.zip` | zip file (~15 MB) |
| `passenger-zip` | `/api/v1/trains/gtfs-passenger.zip` | zip file (~6 MB) |

### Examples

Vehicle positions from production:

```bash
pnpm gtfs
```
OR
```bash
pnpm gtfs --env prd --api locations
```

Trip updates from the test environment:

```bash
pnpm gtfs --env tst --api updates
```

JSON goes to stdout and progress messages to stderr, so the output can be piped into `jq`:

```bash
pnpm gtfs | jq '.entity[].vehicle.trip.tripId'
```

Download a static GTFS archive. These contain plain CSV files and are large, so they are
saved as-is instead of being converted to JSON:

```bash
pnpm gtfs --api passenger-zip --out gtfs-passenger.zip
unzip -l gtfs-passenger.zip
unzip -p gtfs-passenger.zip trips.txt | head
```

Fetch data for a specific date:

```bash
pnpm gtfs --api updates --date 2026-10-01
```

Save realtime JSON to a file:

```bash
pnpm gtfs --api updates --out updates.json
```

Find a specific train in the locations feed:

```bash
pnpm gtfs | jq '.entity[] | select(.vehicle.vehicle.id == "9672")'
```

Count delayed stops in the updates feed:

```bash
pnpm gtfs --api updates \
  | jq '[.entity[].tripUpdate.stopTimeUpdate[]? | select(.arrival.delay > 0)] | length'
```

## Schema

Decoding uses the official
[`gtfs-realtime-bindings`](https://www.npmjs.com/package/gtfs-realtime-bindings) package.
The matching `.proto` schema used by the backend lives in
`AvoinDataUpdater/src/main/resources/schema/gtfs-realtime/gtfs-realtime.proto`.
