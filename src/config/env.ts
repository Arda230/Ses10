export type Environment = "development" | "test" | "production";

export interface AppConfig {
  environment: Environment;
  host: string;
  port: number;
  logLevel: "debug" | "info" | "warn" | "error";
  livekit: { url: string; apiKey: string; apiSecret: string };
  databaseUrl: string;
  auth: { cookieName: string; cookieSecure: boolean; sessionTtlSeconds: number };
}

const environments: readonly Environment[] = [
  "development",
  "test",
  "production",
];
const logLevels = ["debug", "info", "warn", "error"] as const;

function readPort(value: string | undefined): number {
  const port = Number(value ?? "3000");

  if (!Number.isInteger(port) || port < 1 || port > 65_535) {
    throw new Error("PORT must be an integer between 1 and 65535.");
  }

  return port;
}

function readPositiveInteger(name: string, value: string | undefined, fallback: number): number {
  const parsed = Number(value ?? fallback);
  if (!Number.isInteger(parsed) || parsed < 1) throw new Error(name + " must be a positive integer.");
  return parsed;
}

export function loadConfig(source: NodeJS.ProcessEnv = process.env): AppConfig {
  const environment = source.NODE_ENV ?? "development";
  const logLevel = source.LOG_LEVEL ?? "info";

  if (!environments.includes(environment as Environment)) {
    throw new Error("NODE_ENV must be development, test, or production.");
  }

  if (!logLevels.includes(logLevel as (typeof logLevels)[number])) {
    throw new Error("LOG_LEVEL must be debug, info, warn, or error.");
  }

  const livekitUrl = source.LIVEKIT_URL?.trim();
  const livekitApiKey = source.LIVEKIT_API_KEY?.trim();
  const livekitApiSecret = source.LIVEKIT_API_SECRET?.trim();
  const databaseUrl = source.DATABASE_URL?.trim();
  if (!livekitUrl || !livekitApiKey || !livekitApiSecret) {
    throw new Error("LIVEKIT_URL, LIVEKIT_API_KEY and LIVEKIT_API_SECRET must be set.");
  }
  try {
    new URL(livekitUrl);
  } catch {
    throw new Error("LIVEKIT_URL must be a valid URL.");
  }
  if (!databaseUrl) throw new Error("DATABASE_URL must be set.");
  try { new URL(databaseUrl); } catch { throw new Error("DATABASE_URL must be a valid URL."); }

  return {
    environment: environment as Environment,
    host: source.HOST ?? "127.0.0.1",
    port: readPort(source.PORT),
    logLevel: logLevel as AppConfig["logLevel"],
    livekit: { url: livekitUrl, apiKey: livekitApiKey, apiSecret: livekitApiSecret },
    databaseUrl,
    auth: {
      cookieName: source.SESSION_COOKIE_NAME?.trim() || "ses10_session",
      cookieSecure: environment === "production",
      sessionTtlSeconds: readPositiveInteger("SESSION_TTL_SECONDS", source.SESSION_TTL_SECONDS, 604_800),
    },
  };
}
