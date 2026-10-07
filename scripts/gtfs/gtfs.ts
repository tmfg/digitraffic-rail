#!/usr/bin/env tsx
/**
 * Fetches a Digitraffic Rail GTFS or GTFS Realtime feed.
 *
 * Realtime feeds are protobuf encoded and are decoded to JSON on stdout.
 * GTFS zip feeds are large static archives and are written to a file as-is.
 *
 * Usage:
 *   pnpm gtfs [--env tst|prd] [--api <api>] [--date <YYYY-MM-DD>] [--out <file>]
 */
import { writeFile } from "node:fs/promises";
import GtfsRealtimeBindings from "gtfs-realtime-bindings";

const { transit_realtime } = GtfsRealtimeBindings;

const HOSTS = {
  tst: "https://rata-beta.digitraffic.fi",
  prd: "https://rata.digitraffic.fi",
} as const;

type Env = keyof typeof HOSTS;

type Api = {
  readonly path: string;
  readonly kind: "realtime" | "zip";
  readonly description: string;
};

const APIS = {
  locations: {
    path: "/api/v1/trains/gtfs-rt-locations",
    kind: "realtime",
    description: "GTFS Realtime vehicle positions",
  },
  updates: {
    path: "/api/v1/trains/gtfs-rt-updates",
    kind: "realtime",
    description: "GTFS Realtime trip updates",
  },
  "all-zip": {
    path: "/api/v1/trains/gtfs-all.zip",
    kind: "zip",
    description: "Static GTFS archive, all trains",
  },
  "passenger-zip": {
    path: "/api/v1/trains/gtfs-passenger.zip",
    kind: "zip",
    description: "Static GTFS archive, passenger trains only",
  },
} as const satisfies Record<string, Api>;

type ApiName = keyof typeof APIS;

const DEFAULT_ENV: Env = "prd";
const DEFAULT_API: ApiName = "locations";

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

function isEnv(value: string): value is Env {
  return value in HOSTS;
}

function isApiName(value: string): value is ApiName {
  return value in APIS;
}

function usage(): string {
  const apis = Object.entries(APIS)
    .map(([name, api]) => `    ${name.padEnd(15)}${api.description}`)
    .join("\n");

  return `Usage: pnpm gtfs [options]

Options:
  -e, --env <tst|prd>   Environment to query (default: ${DEFAULT_ENV})
  -a, --api <api>       API to fetch (default: ${DEFAULT_API})
  -d, --date <date>     Fetch data for a given date (YYYY-MM-DD).
                        Maps to the 'on_date' query parameter; defaults to
                        the current date when omitted.
  -o, --out <file>      Write output to a file instead of stdout
                        (required for zip APIs)
  -h, --help            Show this help

Environments:
    tst            ${HOSTS.tst}
    prd            ${HOSTS.prd}

APIs:
${apis}`;
}

type Options = {
  readonly env: Env;
  readonly api: ApiName;
  readonly date: string | undefined;
  readonly out: string | undefined;
};

function parseArgs(argv: readonly string[]): Options {
  let env: Env = DEFAULT_ENV;
  let api: ApiName = DEFAULT_API;
  let date: string | undefined;
  let out: string | undefined;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];

    if (arg === "-h" || arg === "--help") {
      console.log(usage());
      process.exit(0);
    }

    if (arg === "-e" || arg === "--env") {
      const value = argv[++i];
      if (value === undefined || !isEnv(value)) {
        throw new Error(
          `Invalid --env '${value ?? ""}'. Expected one of: ${Object.keys(HOSTS).join(", ")}`,
        );
      }
      env = value;
      continue;
    }

    if (arg === "-a" || arg === "--api") {
      const value = argv[++i];
      if (value === undefined || !isApiName(value)) {
        throw new Error(
          `Invalid --api '${value ?? ""}'. Expected one of: ${Object.keys(APIS).join(", ")}`,
        );
      }
      api = value;
      continue;
    }

    if (arg === "-d" || arg === "--date") {
      const value = argv[++i];
      if (value === undefined || !ISO_DATE.test(value)) {
        throw new Error(
          `Invalid --date '${value ?? ""}'. Expected format YYYY-MM-DD`,
        );
      }
      date = value;
      continue;
    }

    if (arg === "-o" || arg === "--out") {
      const value = argv[++i];
      if (value === undefined) {
        throw new Error("--out requires a file name");
      }
      out = value;
      continue;
    }

    throw new Error(`Unknown argument '${arg}'`);
  }

  return { env, api, date, out };
}

async function fetchFeed(url: URL): Promise<Response> {
  const response = await fetch(url, {
    headers: {
      // Digitraffic responds with 406 Not Acceptable without this header
      "Accept-Encoding": "gzip",
      "Digitraffic-User": "digitraffic-rail/gtfs-script",
    },
  });

  if (!response.ok) {
    throw new Error(
      `HTTP ${response.status} ${response.statusText}: ${await response.text()}`,
    );
  }

  return response;
}

function decodeRealtime(payload: Uint8Array): string {
  const feed = transit_realtime.FeedMessage.decode(payload);

  const json = transit_realtime.FeedMessage.toObject(feed, {
    // int64 fields (timestamps) exceed the safe integer range of a JS number
    longs: String,
    enums: String,
    defaults: false,
  });

  return JSON.stringify(json, null, 2);
}

async function main(): Promise<void> {
  const { env, api, date, out } = parseArgs(process.argv.slice(2));
  const definition = APIS[api];
  const url = new URL(definition.path, HOSTS[env]);

  if (date !== undefined) {
    url.searchParams.set("on_date", date);
  }

  if (definition.kind === "zip" && out === undefined) {
    throw new Error(
      `API '${api}' returns a large zip archive; use --out <file> to save it.`,
    );
  }

  console.error(`Fetching ${url}`);
  const response = await fetchFeed(url);
  const payload = new Uint8Array(await response.arrayBuffer());

  if (definition.kind === "zip") {
    await writeFile(out as string, payload);
    console.error(
      `Wrote ${payload.byteLength} bytes to ${out}. Inspect with: unzip -l ${out}`,
    );
    return;
  }

  const json = decodeRealtime(payload);

  if (out === undefined) {
    console.log(json);
  } else {
    await writeFile(out, json);
    console.error(`Wrote ${out}`);
  }
}

try {
  await main();
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
