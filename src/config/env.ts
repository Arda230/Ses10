export type Environment = "development" | "test" | "production";

export interface AppConfig {
  environment: Environment;
  host: string;
  port: number;
  logLevel: "debug" | "info" | "warn" | "error";
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

export function loadConfig(source: NodeJS.ProcessEnv = process.env): AppConfig {
  const environment = source.NODE_ENV ?? "development";
  const logLevel = source.LOG_LEVEL ?? "info";

  if (!environments.includes(environment as Environment)) {
    throw new Error("NODE_ENV must be development, test, or production.");
  }

  if (!logLevels.includes(logLevel as (typeof logLevels)[number])) {
    throw new Error("LOG_LEVEL must be debug, info, warn, or error.");
  }

  return {
    environment: environment as Environment,
    host: source.HOST ?? "127.0.0.1",
    port: readPort(source.PORT),
    logLevel: logLevel as AppConfig["logLevel"],
  };
}
